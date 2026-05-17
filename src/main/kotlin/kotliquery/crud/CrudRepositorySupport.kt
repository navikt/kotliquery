package kotliquery.crud

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.repository.Query
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.sql.Statement
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.kotlinFunction

/**
 * Creates a proxy implementation of the given [CrudRepository] subinterface.
 *
 * Standard CRUD methods are implemented automatically based on the entity's
 * [Table], [Id], [Column], and [GeneratedValue] annotations.
 * Additional methods annotated with [Query] are also supported.
 *
 * By default the row mapper is auto-generated from the entity's primary constructor.
 * Pass a custom [mapper] to override the auto-generated mapping.
 *
 * ```kotlin
 * val repo = session.asCrudRepository<MemberRepository>()
 * ```
 *
 * @param T the repository interface type (must extend [CrudRepository])
 * @param mapper optional custom row mapper; defaults to reflection-based auto-mapping
 * @return a proxy implementation of the interface
 */
inline fun <reified T> Session.asCrudRepository(noinline mapper: ((Row) -> Any?)? = null): T {
    val interfaceClass = T::class.java
    require(interfaceClass.isInterface) { "${interfaceClass.name} must be an interface" }
    require(CrudRepository::class.java.isAssignableFrom(interfaceClass)) {
        "${interfaceClass.name} must extend CrudRepository"
    }

    val (entityClass, idClass) = resolveTypeArguments(interfaceClass)
    val metadata = EntityMetadata.resolve(entityClass.kotlin, idClass.kotlin)
    val effectiveMapper = mapper ?: metadata.autoMapper

    @Suppress("UNCHECKED_CAST")
    return Proxy.newProxyInstance(
        interfaceClass.classLoader,
        arrayOf(interfaceClass),
        CrudRepositoryInvocationHandler(this, metadata, effectiveMapper),
    ) as T
}

@PublishedApi
internal fun resolveTypeArguments(interfaceClass: Class<*>): Pair<Class<*>, Class<*>> {
    val crudType =
        interfaceClass.genericInterfaces
            .filterIsInstance<ParameterizedType>()
            .firstOrNull {
                val raw = it.rawType as? Class<*>
                raw != null && CrudRepository::class.java.isAssignableFrom(raw)
            }
            ?: throw IllegalArgumentException(
                "${interfaceClass.name} must directly extend CrudRepository<T, ID>",
            )

    @Suppress("UNCHECKED_CAST")
    val entityClass = crudType.actualTypeArguments[0] as Class<*>

    @Suppress("UNCHECKED_CAST")
    val idClass = crudType.actualTypeArguments[1] as Class<*>
    return entityClass to idClass
}

@PublishedApi
internal class EntityMetadata<T : Any>(
    val entityClass: KClass<T>,
    val tableName: String,
    val columns: List<ColumnMapping>,
    val idColumn: ColumnMapping,
    val hasGeneratedValue: Boolean,
) {
    val nonIdColumns: List<ColumnMapping> = columns.filter { it !== idColumn }
    val insertColumns: List<ColumnMapping> = if (hasGeneratedValue) nonIdColumns else columns
    val autoMapper: (Row) -> Any = buildAutoMapper()

    @Suppress("UNCHECKED_CAST")
    fun getIdValue(entity: T): Any? = idColumn.property.get(entity)

    fun toParamMap(
        entity: T,
        cols: List<ColumnMapping>,
    ): Map<String, Any?> = cols.associate { it.propertyName to it.property.get(entity) }

    fun reconstructWithId(
        entity: T,
        generatedKey: Long,
    ): T {
        val constructor =
            entityClass.primaryConstructor
                ?: throw IllegalStateException("${entityClass.simpleName} must have a primary constructor")
        val args =
            constructor.parameters.associateWith { param ->
                if (param.name == idColumn.propertyName) {
                    coerceId(generatedKey, idColumn.type)
                } else {
                    val col = columns.first { it.propertyName == param.name }
                    col.property.get(entity)
                }
            }
        return constructor.callBy(args)
    }

    private fun coerceId(
        key: Long,
        type: KType,
    ): Any =
        when (type.classifier) {
            Int::class -> key.toInt()
            Short::class -> key.toShort()
            else -> key
        }

    private fun buildAutoMapper(): (Row) -> Any {
        val constructor =
            entityClass.primaryConstructor
                ?: throw IllegalStateException("${entityClass.simpleName} must have a primary constructor")
        val paramColumns =
            constructor.parameters.map { param ->
                val col =
                    columns.firstOrNull { it.propertyName == param.name }
                        ?: throw IllegalStateException(
                            "No column mapping for constructor parameter '${param.name}' " +
                                "in ${entityClass.simpleName}",
                        )
                param to col
            }
        return { row ->
            val args =
                paramColumns.associate { (param, col) ->
                    param to readColumn(row, col.columnName, param.type)
                }
            constructor.callBy(args)
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> resolve(
            entityClass: KClass<T>,
            idClass: KClass<*>,
        ): EntityMetadata<T> {
            val tableAnnotation =
                entityClass.findAnnotation<Table>()
                    ?: throw IllegalStateException(
                        "Entity ${entityClass.simpleName} must be annotated with @Table",
                    )

            val constructor =
                entityClass.primaryConstructor
                    ?: throw IllegalStateException(
                        "Entity ${entityClass.simpleName} must have a primary constructor",
                    )

            val properties = entityClass.memberProperties.associateBy { it.name }

            val columns =
                constructor.parameters.map { param ->
                    val prop =
                        properties[param.name]
                            ?: throw IllegalStateException(
                                "No property found for constructor parameter '${param.name}' " +
                                    "in ${entityClass.simpleName}",
                            )
                    val columnAnnotation = prop.findAnnotation<Column>()
                    val isId = prop.findAnnotation<Id>() != null
                    val isGenerated = prop.findAnnotation<GeneratedValue>() != null
                    ColumnMapping(
                        propertyName = param.name!!,
                        columnName = columnAnnotation?.name ?: param.name!!,
                        isId = isId,
                        isGeneratedValue = isGenerated,
                        type = param.type,
                        rawProperty = prop,
                    )
                }

            val idColumn =
                columns.singleOrNull { it.isId }
                    ?: throw IllegalStateException(
                        "Entity ${entityClass.simpleName} must have exactly one property annotated with @Id",
                    )

            return EntityMetadata(
                entityClass = entityClass,
                tableName = tableAnnotation.name,
                columns = columns,
                idColumn = idColumn,
                hasGeneratedValue = idColumn.isGeneratedValue,
            )
        }

        private fun readColumn(
            row: Row,
            column: String,
            type: KType,
        ): Any? {
            val nullable = type.isMarkedNullable
            return when (type.classifier) {
                String::class -> if (nullable) row.stringOrNull(column) else row.string(column)
                Int::class -> if (nullable) row.intOrNull(column) else row.int(column)
                Long::class -> if (nullable) row.longOrNull(column) else row.long(column)
                Boolean::class -> row.boolean(column)
                Double::class -> if (nullable) row.doubleOrNull(column) else row.double(column)
                Float::class -> if (nullable) row.floatOrNull(column) else row.float(column)
                Short::class -> if (nullable) row.shortOrNull(column) else row.short(column)
                Byte::class -> if (nullable) row.byteOrNull(column) else row.byte(column)
                BigDecimal::class -> if (nullable) row.bigDecimalOrNull(column) else row.bigDecimal(column)
                LocalDateTime::class -> if (nullable) row.localDateTimeOrNull(column) else row.localDateTime(column)
                LocalDate::class -> if (nullable) row.localDateOrNull(column) else row.localDate(column)
                LocalTime::class -> if (nullable) row.localTimeOrNull(column) else row.localTime(column)
                ZonedDateTime::class -> if (nullable) row.zonedDateTimeOrNull(column) else row.zonedDateTime(column)
                OffsetDateTime::class -> if (nullable) row.offsetDateTimeOrNull(column) else row.offsetDateTime(column)
                Instant::class -> if (nullable) row.instantOrNull(column) else row.instant(column)
                java.util.UUID::class -> if (nullable) row.uuidOrNull(column) else row.uuid(column)
                ByteArray::class -> if (nullable) row.bytesOrNull(column) else row.bytes(column)
                else -> row.anyOrNull(column)
            }
        }
    }
}

@PublishedApi
internal class ColumnMapping(
    val propertyName: String,
    val columnName: String,
    val isId: Boolean,
    val isGeneratedValue: Boolean,
    val type: KType,
    private val rawProperty: KProperty1<*, *>,
) {
    @Suppress("UNCHECKED_CAST")
    val property: KProperty1<Any, *>
        get() = rawProperty as KProperty1<Any, *>
}

@PublishedApi
internal class CrudRepositoryInvocationHandler<T : Any>(
    private val session: Session,
    private val metadata: EntityMetadata<T>,
    private val mapper: (Row) -> Any?,
) : InvocationHandler {
    private val crudMethods =
        setOf(
            "save",
            "saveAll",
            "findById",
            "findAll",
            "findAllById",
            "existsById",
            "count",
            "deleteById",
            "delete",
            "deleteAllById",
            "deleteAll",
        )

    override fun invoke(
        proxy: Any,
        method: Method,
        args: Array<out Any?>?,
    ): Any? {
        when (method.name) {
            "toString" -> {
                val name =
                    proxy.javaClass.interfaces
                        .firstOrNull()
                        ?.simpleName ?: "CrudRepository"
                return "$name(proxy)"
            }
            "hashCode" -> return System.identityHashCode(proxy)
            "equals" -> return proxy === args?.firstOrNull()
        }

        if (method.isDefault && method.getAnnotation(Query::class.java) == null) {
            return InvocationHandler.invokeDefault(proxy, method, *(args ?: emptyArray()))
        }

        val queryAnnotation = method.getAnnotation(Query::class.java)
        if (queryAnnotation != null) {
            return executeAnnotatedQuery(method, queryAnnotation, args)
        }

        if (method.name in crudMethods) {
            return executeCrudMethod(method.name, args)
        }

        throw IllegalStateException(
            "Method '${method.name}' on ${method.declaringClass.simpleName} is neither a " +
                "CrudRepository method nor annotated with @Query",
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun executeCrudMethod(
        methodName: String,
        args: Array<out Any?>?,
    ): Any? {
        val table = metadata.tableName
        val idCol = metadata.idColumn.columnName
        val idProp = metadata.idColumn.propertyName

        try {
            return when (methodName) {
                "save" -> {
                    val entity = args!![0] as T
                    if (isNewEntity(entity)) insertEntity(entity) else updateEntity(entity)
                }
                "saveAll" -> {
                    val entities = args!![0] as Iterable<T>
                    entities.map { entity ->
                        if (isNewEntity(entity)) insertEntity(entity) else updateEntity(entity)
                    }
                }
                "findById" -> {
                    val sql = "SELECT * FROM $table WHERE $idCol = :$idProp"
                    session.single(queryOf(sql, mapOf(idProp to args!![0])), mapper)
                }
                "findAll" -> {
                    session.list(queryOf("SELECT * FROM $table"), mapper)
                }
                "findAllById" -> {
                    val ids = (args!![0] as Iterable<*>).toList()
                    if (ids.isEmpty()) return emptyList<Any>()
                    val paramMap = ids.mapIndexed { i, id -> "id_$i" to id }.toMap()
                    val placeholders = paramMap.keys.joinToString(", ") { ":$it" }
                    val sql = "SELECT * FROM $table WHERE $idCol IN ($placeholders)"
                    session.list(queryOf(sql, paramMap), mapper)
                }
                "existsById" -> {
                    val sql = "SELECT COUNT(*) AS cnt FROM $table WHERE $idCol = :$idProp"
                    val count =
                        session.single(queryOf(sql, mapOf(idProp to args!![0]))) { row ->
                            row.long("cnt")
                        }
                    (count ?: 0L) > 0L
                }
                "count" -> {
                    session.single(queryOf("SELECT COUNT(*) AS cnt FROM $table")) { row ->
                        row.long("cnt")
                    } ?: 0L
                }
                "deleteById" -> {
                    val sql = "DELETE FROM $table WHERE $idCol = :$idProp"
                    session.update(queryOf(sql, mapOf(idProp to args!![0])))
                    null
                }
                "delete" -> {
                    val entity = args!![0] as T
                    val idValue = metadata.getIdValue(entity)
                    val sql = "DELETE FROM $table WHERE $idCol = :$idProp"
                    session.update(queryOf(sql, mapOf(idProp to idValue)))
                    null
                }
                "deleteAllById" -> {
                    val ids = (args!![0] as Iterable<*>).toList()
                    if (ids.isNotEmpty()) {
                        val paramMap = ids.mapIndexed { i, id -> "id_$i" to id }.toMap()
                        val placeholders = paramMap.keys.joinToString(", ") { ":$it" }
                        val sql = "DELETE FROM $table WHERE $idCol IN ($placeholders)"
                        session.update(queryOf(sql, paramMap))
                    }
                    null
                }
                "deleteAll" -> {
                    session.update(queryOf("DELETE FROM $table"))
                    null
                }
                else -> throw IllegalStateException("Unknown CRUD method: $methodName")
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun isNewEntity(entity: T): Boolean {
        val idValue = metadata.getIdValue(entity)
        return when {
            idValue == null -> true
            metadata.hasGeneratedValue && idValue is Number && idValue.toLong() == 0L -> true
            !metadata.hasGeneratedValue -> {
                val sql =
                    "SELECT COUNT(*) AS cnt FROM ${metadata.tableName} " +
                        "WHERE ${metadata.idColumn.columnName} = :${metadata.idColumn.propertyName}"
                val count =
                    session.single(
                        queryOf(sql, mapOf(metadata.idColumn.propertyName to idValue)),
                    ) { row -> row.long("cnt") }
                (count ?: 0L) == 0L
            }
            else -> false
        }
    }

    private fun insertEntity(entity: T): T {
        val cols = metadata.insertColumns
        val columnNames = cols.joinToString(", ") { it.columnName }
        val placeholders = cols.joinToString(", ") { ":${it.propertyName}" }
        val sql = "INSERT INTO ${metadata.tableName} ($columnNames) VALUES ($placeholders)"
        val params = metadata.toParamMap(entity, cols)

        if (metadata.hasGeneratedValue) {
            val query = queryOf(sql, params)
            val generatedKey = insertAndReturnKey(query)
            return if (generatedKey != null) {
                metadata.reconstructWithId(entity, generatedKey)
            } else {
                entity
            }
        } else {
            session.update(queryOf(sql, params))
            return entity
        }
    }

    private fun insertAndReturnKey(query: kotliquery.Query): Long? {
        val stmt =
            session.connection.underlying.prepareStatement(
                query.cleanStatement,
                Statement.RETURN_GENERATED_KEYS,
            )
        return try {
            session.populateParams(query, stmt)
            if (stmt.executeUpdate() > 0) {
                val rs = stmt.generatedKeys
                if (rs.next()) rs.getLong(1) else null
            } else {
                null
            }
        } finally {
            stmt.close()
        }
    }

    private fun updateEntity(entity: T): T {
        val setCols = metadata.nonIdColumns
        val setClause = setCols.joinToString(", ") { "${it.columnName} = :${it.propertyName}" }
        val sql =
            "UPDATE ${metadata.tableName} SET $setClause " +
                "WHERE ${metadata.idColumn.columnName} = :${metadata.idColumn.propertyName}"
        val params = metadata.toParamMap(entity, metadata.columns)
        session.update(queryOf(sql, params))
        return entity
    }

    private fun executeAnnotatedQuery(
        method: Method,
        annotation: Query,
        args: Array<out Any?>?,
    ): Any? {
        val sql = annotation.value
        val paramMap = buildParamMap(method, args)
        val query = queryOf(sql, paramMap)

        try {
            return if (sql.trimStart().uppercase().startsWith("SELECT")) {
                if (List::class.java.isAssignableFrom(method.returnType)) {
                    session.list(query, mapper)
                } else {
                    session.single(query, mapper)
                }
            } else {
                executeDml(method, query)
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun buildParamMap(
        method: Method,
        args: Array<out Any?>?,
    ): Map<String, Any?> {
        if (args.isNullOrEmpty()) return emptyMap()

        val kFunction = method.kotlinFunction
        val paramNames =
            kFunction
                ?.parameters
                ?.filter { it.kind == KParameter.Kind.VALUE }
                ?.map {
                    it.name ?: throw IllegalStateException(
                        "Parameter name not available for method '${method.name}'. " +
                            "Ensure kotlin-reflect is on the classpath.",
                    )
                } ?: method.parameters.map { it.name }

        require(paramNames.size == args.size) {
            "Parameter count mismatch for '${method.name}': expected ${paramNames.size}, got ${args.size}"
        }

        return paramNames.zip(args.toList()).toMap()
    }

    private fun executeDml(
        method: Method,
        query: kotliquery.Query,
    ): Any? {
        val returnType = method.returnType
        return when (returnType) {
            Int::class.javaPrimitiveType, Int::class.javaObjectType ->
                session.update(query)
            Long::class.javaPrimitiveType, Long::class.javaObjectType ->
                session.updateAndReturnGeneratedKey(query)
            Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType ->
                session.update(query) > 0
            Void.TYPE -> {
                session.execute(query)
                null
            }
            else ->
                throw IllegalStateException(
                    "Unsupported return type '${returnType.name}' for DML method '${method.name}'. " +
                        "Expected Int, Long, Boolean, or Unit.",
                )
        }
    }
}
