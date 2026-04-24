package kotliquery.crud

/**
 * Base interface for CRUD operations on an entity.
 *
 * Define a repository by extending this interface with concrete entity and ID types:
 *
 * ```kotlin
 * @Table("members")
 * data class Member(
 *     @Id @GeneratedValue val id: Long = 0,
 *     val name: String,
 * )
 *
 * interface MemberRepository : CrudRepository<Member, Long>
 * ```
 *
 * Create an instance via [asCrudRepository]:
 *
 * ```kotlin
 * val repo = session.asCrudRepository<MemberRepository>()
 * val saved = repo.save(Member(name = "Alice"))
 * ```
 *
 * @param T the entity type, annotated with [Table] and having exactly one [Id] property
 * @param ID the type of the entity's primary key
 * @see asCrudRepository
 */
interface CrudRepository<T : Any, ID : Any> {
    /** Inserts a new entity or updates an existing one. Returns the (potentially updated) entity. */
    fun save(entity: T): T

    /** Saves all given entities. Returns the saved entities as a list. */
    fun saveAll(entities: Iterable<T>): List<T>

    /** Returns the entity with the given [id], or `null` if none exists. */
    fun findById(id: ID): T?

    /** Returns all entities in the table. */
    fun findAll(): List<T>

    /** Returns all entities whose ID is contained in [ids]. */
    fun findAllById(ids: Iterable<ID>): List<T>

    /** Returns `true` if an entity with the given [id] exists. */
    fun existsById(id: ID): Boolean

    /** Returns the total number of entities in the table. */
    fun count(): Long

    /** Deletes the entity with the given [id]. */
    fun deleteById(id: ID)

    /** Deletes the given [entity] (by its ID). */
    fun delete(entity: T)

    /** Deletes all entities whose ID is contained in [ids]. */
    fun deleteAllById(ids: Iterable<ID>)

    /** Deletes all entities in the table. */
    fun deleteAll()
}
