package kotliquery.repository

/**
 * Marks an interface method as a database query.
 *
 * The SQL statement uses named parameters (`:paramName` syntax) that are automatically
 * bound from the method's parameter names.
 *
 * **Return type determines execution mode:**
 *
 * *SELECT queries* (SQL starting with `SELECT`):
 * - `List<T>` returns all matching rows mapped via the provided row mapper
 * - Any other type returns a single row or `null`
 *
 * *DML queries* (INSERT, UPDATE, DELETE, etc.):
 * - `Int` returns the affected row count
 * - `Long` or `Long?` returns the generated key
 * - `Boolean` returns execution success
 * - `Unit` executes without returning a result
 *
 * Example:
 * ```kotlin
 * interface PersonRepository {
 *     @Query("SELECT * FROM person WHERE lastname = :lastname")
 *     fun findByLastname(lastname: String): List<Person>
 *
 *     @Query("INSERT INTO person (name) VALUES (:name)")
 *     fun insert(name: String): Int
 * }
 * ```
 *
 * @param value the SQL statement to execute
 * @see asRepository
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Query(
    val value: String,
)
