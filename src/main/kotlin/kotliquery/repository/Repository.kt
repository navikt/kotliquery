package kotliquery.repository

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.kotlinFunction

/**
 * Creates a proxy implementation of the given repository interface, where methods
 * annotated with [Query] are executed against this session.
 *
 * For SELECT queries, a [mapper] must be provided to convert [Row] to the target type.
 * DML queries (INSERT, UPDATE, DELETE) do not require a mapper.
 *
 * Example:
 * ```kotlin
 * val repo = session.asRepository<PersonRepository> { row ->
 *     Person(row.int("id"), row.string("name"))
 * }
 * val people = repo.findByLastname("Smith")
 * ```
 *
 * @param T the repository interface type
 * @param mapper row mapper for SELECT queries
 * @return a proxy implementation of the interface
 */
inline fun <reified T> Session.asRepository(noinline mapper: ((Row) -> Any?)? = null): T {
    val interfaceClass = T::class.java
    require(interfaceClass.isInterface) { "${interfaceClass.name} must be an interface" }

    @Suppress("UNCHECKED_CAST")
    return Proxy.newProxyInstance(
        interfaceClass.classLoader,
        arrayOf(interfaceClass),
        RepositoryInvocationHandler(this, mapper),
    ) as T
}

@PublishedApi
internal class RepositoryInvocationHandler(
    private val session: Session,
    private val mapper: ((Row) -> Any?)?,
) : InvocationHandler {
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
                        ?.simpleName ?: "Repository"
                return "$name(proxy)"
            }
            "hashCode" -> return System.identityHashCode(proxy)
            "equals" -> return proxy === args?.firstOrNull()
        }

        if (method.isDefault && method.getAnnotation(Query::class.java) == null) {
            return InvocationHandler.invokeDefault(proxy, method, *(args ?: emptyArray()))
        }

        val queryAnnotation =
            method.getAnnotation(Query::class.java)
                ?: throw IllegalStateException(
                    "Method '${method.name}' on ${method.declaringClass.simpleName} is not annotated with @Query",
                )

        val sql = queryAnnotation.value
        val paramMap = buildParamMap(method, args)
        val query = queryOf(sql, paramMap)

        try {
            return if (isSelectQuery(sql)) {
                requireNotNull(mapper) {
                    "A row mapper must be provided for SELECT query method '${method.name}'"
                }
                if (isList(method)) {
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

    private fun isSelectQuery(sql: String): Boolean = sql.trimStart().uppercase().startsWith("SELECT")

    private fun isList(method: Method): Boolean = List::class.java.isAssignableFrom(method.returnType)
}
