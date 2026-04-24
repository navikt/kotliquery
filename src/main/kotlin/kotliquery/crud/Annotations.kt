package kotliquery.crud

/**
 * Maps an entity class to a database table.
 *
 * ```kotlin
 * @Table("members")
 * data class Member(@Id val id: Long, val name: String)
 * ```
 *
 * @param name the database table name
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Table(
    val name: String,
)

/**
 * Marks the primary-key property of an entity.
 *
 * Exactly one property in an entity class annotated with [Table] must carry this annotation.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Id

/**
 * Overrides the column name for a property.
 *
 * By default, the property name is used as the column name.
 *
 * ```kotlin
 * @Column("full_name")
 * val name: String
 * ```
 *
 * @param name the database column name
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Column(
    val name: String,
)

/**
 * Indicates that the database generates the value for this column (e.g. auto-increment).
 *
 * Used together with [Id] on the primary-key property. When present, [CrudRepository.save]
 * omits this column from INSERT statements and retrieves the generated key from the database.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class GeneratedValue
