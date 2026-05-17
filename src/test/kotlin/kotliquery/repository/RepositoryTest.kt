package kotliquery.repository

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.testDataSource
import kotliquery.using
import java.time.ZonedDateTime
import java.util.Date

data class Member(
    val id: Int,
    val name: String?,
    val createdAt: ZonedDateTime,
)

private val toMember: (Row) -> Member = { row ->
    Member(row.int("id"), row.stringOrNull("name"), row.zonedDateTime("created_at"))
}

interface MemberRepository {
    @Query("SELECT id, name, created_at FROM members")
    fun findAll(): List<Member>

    @Query("SELECT id, name, created_at FROM members WHERE name = :name")
    fun findByName(name: String): List<Member>

    @Query("SELECT id, name, created_at FROM members WHERE id = :id")
    fun findById(id: Int): Member?

    @Query("INSERT INTO members (name, created_at) VALUES (:name, :createdAt)")
    fun insert(
        name: String,
        createdAt: Date,
    ): Int

    @Query("UPDATE members SET name = :newName WHERE name = :oldName")
    fun updateName(
        oldName: String,
        newName: String,
    ): Int

    @Query("DELETE FROM members WHERE name = :name")
    fun deleteByName(name: String): Int

    @Query("DELETE FROM members")
    fun deleteAll(): Boolean
}

interface MemberRepositoryWithGeneratedKey {
    @Query("INSERT INTO members (name, created_at) VALUES (:name, :createdAt)")
    fun insert(
        name: String,
        createdAt: Date,
    ): Long?
}

interface MemberRepositoryWithSingleByName {
    @Query("SELECT id, name, created_at FROM members WHERE name = :name")
    fun findOneByName(name: String): Member?
}

interface MemberRepositoryWithDefault {
    @Query("SELECT id, name, created_at FROM members WHERE id = :id")
    fun findById(id: Int): Member?

    fun findByIdOrThrow(id: Int): Member = findById(id) ?: throw NoSuchElementException("Member $id not found")
}

interface NotAnnotatedRepository {
    fun findAll(): List<Member>
}

class RepositoryTest :
    FunSpec({

        beforeEach {
            using(sessionOf(testDataSource)) { session ->
                session.execute(queryOf("drop table members if exists"))
                session.execute(
                    queryOf(
                        """
                        create table members (
                            id serial primary key,
                            name varchar(64),
                            created_at timestamp not null
                        )
                        """.trimIndent(),
                    ),
                )
            }
        }

        test("select list returns all rows") {
            using(sessionOf(testDataSource)) { session ->
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Alice", Date()))
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Bob", Date()))

                val repo = session.asRepository<MemberRepository>(toMember)
                val members = repo.findAll()

                members shouldHaveSize 2
                members[0].name shouldBe "Alice"
                members[1].name shouldBe "Bob"
            }
        }

        test("select list with named parameter filters correctly") {
            using(sessionOf(testDataSource)) { session ->
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Alice", Date()))
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Bob", Date()))

                val repo = session.asRepository<MemberRepository>(toMember)

                repo.findByName("Alice") shouldHaveSize 1
                repo.findByName("Alice").first().name shouldBe "Alice"
                repo.findByName("Bob") shouldHaveSize 1
                repo.findByName("Nobody") shouldHaveSize 0
            }
        }

        test("select single returns matching row") {
            using(sessionOf(testDataSource)) { session ->
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Alice", Date()))

                val repo = session.asRepository<MemberRepository>(toMember)
                val alice = repo.findById(1)

                alice.shouldNotBeNull()
                alice.name shouldBe "Alice"
            }
        }

        test("select single returns null when no match") {
            using(sessionOf(testDataSource)) { session ->
                val repo = session.asRepository<MemberRepository>(toMember)
                repo.findById(999).shouldBeNull()
            }
        }

        test("insert returns affected row count") {
            using(sessionOf(testDataSource)) { session ->
                val repo = session.asRepository<MemberRepository>(toMember)

                repo.insert("Alice", Date()) shouldBe 1

                val members = repo.findAll()
                members shouldHaveSize 1
                members.first().name shouldBe "Alice"
            }
        }

        test("update returns affected row count") {
            using(sessionOf(testDataSource)) { session ->
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Alice", Date()))

                val repo = session.asRepository<MemberRepository>(toMember)
                repo.updateName("Alice", "Alicia") shouldBe 1
                repo.findAll().first().name shouldBe "Alicia"
            }
        }

        test("delete returns affected row count") {
            using(sessionOf(testDataSource)) { session ->
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Alice", Date()))
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Bob", Date()))

                val repo = session.asRepository<MemberRepository>(toMember)
                repo.deleteByName("Alice") shouldBe 1
                repo.findAll() shouldHaveSize 1
            }
        }

        test("boolean return true when rows affected") {
            using(sessionOf(testDataSource)) { session ->
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Alice", Date()))

                val repo = session.asRepository<MemberRepository>(toMember)
                repo.deleteAll().shouldBeTrue()
                repo.findAll() shouldHaveSize 0
            }
        }

        test("boolean return false when no rows affected") {
            using(sessionOf(testDataSource)) { session ->
                val repo = session.asRepository<MemberRepository>(toMember)
                repo.deleteAll().shouldBeFalse()
            }
        }

        test("insert and return generated key") {
            using(sessionOf(testDataSource, returnGeneratedKey = true)) { session ->
                val repo = session.asRepository<MemberRepositoryWithGeneratedKey>()

                repo.insert("Alice", Date()) shouldBe 1L
                repo.insert("Bob", Date()) shouldBe 2L
            }
        }

        test("default method delegates to annotated method") {
            using(sessionOf(testDataSource)) { session ->
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Alice", Date()))

                val repo = session.asRepository<MemberRepositoryWithDefault>(toMember)
                repo.findByIdOrThrow(1).name shouldBe "Alice"

                shouldThrow<NoSuchElementException> {
                    repo.findByIdOrThrow(999)
                }
            }
        }

        test("method without @Query throws IllegalStateException") {
            using(sessionOf(testDataSource)) { session ->
                val repo = session.asRepository<NotAnnotatedRepository>(toMember)

                shouldThrow<IllegalStateException> {
                    repo.findAll()
                }
            }
        }

        test("select without mapper throws IllegalArgumentException") {
            using(sessionOf(testDataSource)) { session ->
                val repo = session.asRepository<MemberRepository>()

                shouldThrow<IllegalArgumentException> {
                    repo.findAll()
                }
            }
        }

        test("non-interface type throws IllegalArgumentException") {
            using(sessionOf(testDataSource)) { session ->
                shouldThrow<IllegalArgumentException> {
                    session.asRepository<Member>(toMember)
                }
            }
        }

        test("multiple parameters bind correctly") {
            using(sessionOf(testDataSource)) { session ->
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Alice", Date()))
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Bob", Date()))

                val repo = session.asRepository<MemberRepository>(toMember)
                repo.updateName("Alice", "Charlie") shouldBe 1
                repo.findByName("Charlie") shouldHaveSize 1
                repo.findByName("Alice") shouldHaveSize 0
            }
        }

        test("toString contains interface name") {
            using(sessionOf(testDataSource)) { session ->
                val repo = session.asRepository<MemberRepository>(toMember)
                repo.toString() shouldContain "MemberRepository"
            }
        }

        test("equals and hashCode identity semantics") {
            using(sessionOf(testDataSource)) { session ->
                val repo1 = session.asRepository<MemberRepository>(toMember)
                val repo2 = session.asRepository<MemberRepository>(toMember)

                (repo1 == repo1) shouldBe true
                (repo1 == repo2) shouldBe false
                repo1.hashCode() shouldBe repo1.hashCode()
            }
        }

        test("strict mode propagates through proxy") {
            using(sessionOf(testDataSource, strict = true)) { session ->
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Alice", Date()))
                session.update(queryOf("insert into members (name, created_at) values (?, ?)", "Alice", Date()))

                val repo = session.asRepository<MemberRepositoryWithSingleByName>(toMember)

                val exception =
                    shouldThrow<RuntimeException> {
                        repo.findOneByName("Alice")
                    }
                exception.cause?.message shouldBe "Expected 1 row but received 2."
            }
        }
    })
