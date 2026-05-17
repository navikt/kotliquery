package kotliquery.crud

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotliquery.queryOf
import kotliquery.repository.Query
import kotliquery.sessionOf
import kotliquery.testDataSource
import kotliquery.using

@Table("persons")
data class Person(
    @Id @GeneratedValue val id: Long = 0,
    @Column("full_name") val name: String,
    val age: Int,
)

@Table("tags")
data class Tag(
    @Id val code: String,
    val label: String,
)

interface PersonRepository : CrudRepository<Person, Long> {
    @Query("SELECT * FROM persons WHERE full_name = :name")
    fun findByName(name: String): List<Person>
}

interface TagRepository : CrudRepository<Tag, String>

interface RepoWithDefault : CrudRepository<Person, Long> {
    fun findByIdOrThrow(id: Long): Person = findById(id) ?: throw NoSuchElementException("Person $id not found")
}

interface NotACrudRepo {
    fun findAll(): List<Person>
}

abstract class AbstractClass : CrudRepository<Person, Long>

class CrudRepositoryTest :
    FunSpec({

        beforeEach {
            using(sessionOf(testDataSource)) { session ->
                session.execute(queryOf("DROP TABLE IF EXISTS persons"))
                session.execute(queryOf("DROP TABLE IF EXISTS tags"))
                session.execute(
                    queryOf(
                        """
                        CREATE TABLE persons (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            full_name VARCHAR(255) NOT NULL,
                            age INT NOT NULL
                        )
                        """.trimIndent(),
                    ),
                )
                session.execute(
                    queryOf(
                        """
                        CREATE TABLE tags (
                            code VARCHAR(50) PRIMARY KEY,
                            label VARCHAR(255) NOT NULL
                        )
                        """.trimIndent(),
                    ),
                )
            }
        }

        context("save") {
            test("inserts new entity with generated id") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    val saved = repo.save(Person(name = "Alice", age = 30))

                    saved.id shouldBe 1L
                    saved.name shouldBe "Alice"
                    saved.age shouldBe 30
                }
            }

            test("updates existing entity") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    val saved = repo.save(Person(name = "Alice", age = 30))
                    val updated = repo.save(saved.copy(age = 31))

                    updated.id shouldBe saved.id
                    updated.age shouldBe 31

                    val fetched = repo.findById(saved.id)
                    fetched.shouldNotBeNull()
                    fetched.age shouldBe 31
                }
            }

            test("inserts entity with non-generated id") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<TagRepository>()
                    val saved = repo.save(Tag(code = "kt", label = "Kotlin"))

                    saved.code shouldBe "kt"
                    saved.label shouldBe "Kotlin"

                    repo.findById("kt").shouldNotBeNull().label shouldBe "Kotlin"
                }
            }
        }

        context("saveAll") {
            test("inserts multiple new entities") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    val saved =
                        repo.saveAll(
                            listOf(
                                Person(name = "Alice", age = 30),
                                Person(name = "Bob", age = 25),
                            ),
                        )

                    saved shouldHaveSize 2
                    saved[0].name shouldBe "Alice"
                    saved[1].name shouldBe "Bob"
                    saved.map { it.id } shouldContainExactlyInAnyOrder listOf(1L, 2L)
                }
            }
        }

        context("findById") {
            test("returns entity when found") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.save(Person(name = "Alice", age = 30))

                    val found = repo.findById(1L)
                    found.shouldNotBeNull()
                    found.name shouldBe "Alice"
                }
            }

            test("returns null when not found") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.findById(999L).shouldBeNull()
                }
            }
        }

        context("findAll") {
            test("returns all entities") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.save(Person(name = "Alice", age = 30))
                    repo.save(Person(name = "Bob", age = 25))

                    val all = repo.findAll()
                    all shouldHaveSize 2
                    all.map { it.name } shouldContainExactlyInAnyOrder listOf("Alice", "Bob")
                }
            }

            test("returns empty list when table is empty") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.findAll() shouldHaveSize 0
                }
            }
        }

        context("findAllById") {
            test("returns matching entities") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.save(Person(name = "Alice", age = 30))
                    repo.save(Person(name = "Bob", age = 25))
                    repo.save(Person(name = "Charlie", age = 35))

                    val found = repo.findAllById(listOf(1L, 3L))
                    found shouldHaveSize 2
                    found.map { it.name } shouldContainExactlyInAnyOrder listOf("Alice", "Charlie")
                }
            }

            test("returns empty list for empty ids") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.findAllById(emptyList()) shouldHaveSize 0
                }
            }
        }

        context("existsById") {
            test("returns true when entity exists") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.save(Person(name = "Alice", age = 30))

                    repo.existsById(1L).shouldBeTrue()
                }
            }

            test("returns false when entity does not exist") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.existsById(999L).shouldBeFalse()
                }
            }
        }

        context("count") {
            test("returns number of entities") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.save(Person(name = "Alice", age = 30))
                    repo.save(Person(name = "Bob", age = 25))

                    repo.count() shouldBe 2L
                }
            }

            test("returns zero for empty table") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.count() shouldBe 0L
                }
            }
        }

        context("deleteById") {
            test("deletes existing entity") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.save(Person(name = "Alice", age = 30))

                    repo.deleteById(1L)
                    repo.findById(1L).shouldBeNull()
                }
            }
        }

        context("delete") {
            test("deletes entity by its id") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    val saved = repo.save(Person(name = "Alice", age = 30))

                    repo.delete(saved)
                    repo.findById(saved.id).shouldBeNull()
                }
            }
        }

        context("deleteAllById") {
            test("deletes matching entities") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.save(Person(name = "Alice", age = 30))
                    repo.save(Person(name = "Bob", age = 25))
                    repo.save(Person(name = "Charlie", age = 35))

                    repo.deleteAllById(listOf(1L, 3L))
                    repo.findAll() shouldHaveSize 1
                    repo.findAll().first().name shouldBe "Bob"
                }
            }
        }

        context("deleteAll") {
            test("deletes all entities") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.save(Person(name = "Alice", age = 30))
                    repo.save(Person(name = "Bob", age = 25))

                    repo.deleteAll()
                    repo.findAll() shouldHaveSize 0
                }
            }
        }

        context("@Column name override") {
            test("auto-mapper reads from overridden column name") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    val saved = repo.save(Person(name = "Alice", age = 30))

                    val fetched = repo.findById(saved.id)
                    fetched.shouldNotBeNull()
                    fetched.name shouldBe "Alice"
                }
            }

            test("save writes to overridden column name") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.save(Person(name = "Alice", age = 30))

                    val raw =
                        session.single(
                            queryOf("SELECT full_name FROM persons WHERE id = 1"),
                        ) { row -> row.string("full_name") }
                    raw shouldBe "Alice"
                }
            }
        }

        context("custom @Query methods on CrudRepository") {
            test("executes annotated query alongside CRUD methods") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.save(Person(name = "Alice", age = 30))
                    repo.save(Person(name = "Bob", age = 25))
                    repo.save(Person(name = "Alice", age = 40))

                    val alices = repo.findByName("Alice")
                    alices shouldHaveSize 2
                    alices.map { it.age } shouldContainExactlyInAnyOrder listOf(30, 40)
                }
            }
        }

        context("default methods") {
            test("default method delegates to CRUD method") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<RepoWithDefault>()
                    repo.save(Person(name = "Alice", age = 30))

                    repo.findByIdOrThrow(1L).name shouldBe "Alice"

                    shouldThrow<NoSuchElementException> {
                        repo.findByIdOrThrow(999L)
                    }
                }
            }
        }

        context("custom mapper override") {
            test("uses provided mapper instead of auto-mapper") {
                using(sessionOf(testDataSource)) { session ->
                    val customMapper: (kotliquery.Row) -> Person = { row ->
                        Person(
                            id = row.long("id"),
                            name = row.string("full_name").uppercase(),
                            age = row.int("age"),
                        )
                    }
                    val repo = session.asCrudRepository<PersonRepository>(customMapper)
                    repo.save(Person(name = "alice", age = 30))

                    val found = repo.findById(1L)
                    found.shouldNotBeNull()
                    found.name shouldBe "ALICE"
                }
            }
        }

        context("toString, equals, hashCode") {
            test("toString contains interface name") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.toString() shouldContain "PersonRepository"
                }
            }

            test("equals uses identity semantics") {
                using(sessionOf(testDataSource)) { session ->
                    val repo1 = session.asCrudRepository<PersonRepository>()
                    val repo2 = session.asCrudRepository<PersonRepository>()

                    (repo1 == repo1) shouldBe true
                    (repo1 == repo2) shouldBe false
                }
            }

            test("hashCode is stable") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<PersonRepository>()
                    repo.hashCode() shouldBe repo.hashCode()
                }
            }
        }

        context("error cases") {
            test("non-interface type throws IllegalArgumentException") {
                using(sessionOf(testDataSource)) { session ->
                    shouldThrow<IllegalArgumentException> {
                        session.asCrudRepository<AbstractClass>()
                    }
                }
            }

            test("interface not extending CrudRepository throws IllegalArgumentException") {
                using(sessionOf(testDataSource)) { session ->
                    shouldThrow<IllegalArgumentException> {
                        session.asCrudRepository<NotACrudRepo>()
                    }
                }
            }
        }

        context("string ID (non-generated)") {
            test("full CRUD cycle with string ID") {
                using(sessionOf(testDataSource)) { session ->
                    val repo = session.asCrudRepository<TagRepository>()

                    repo.save(Tag("kt", "Kotlin"))
                    repo.save(Tag("java", "Java"))
                    repo.save(Tag("rs", "Rust"))

                    repo.findAll() shouldHaveSize 3
                    repo.findById("kt").shouldNotBeNull().label shouldBe "Kotlin"
                    repo.existsById("java").shouldBeTrue()
                    repo.existsById("go").shouldBeFalse()
                    repo.count() shouldBe 3L

                    repo.findAllById(listOf("kt", "rs")) shouldHaveSize 2

                    repo.deleteById("java")
                    repo.findAll() shouldHaveSize 2

                    repo.delete(Tag("kt", "Kotlin"))
                    repo.findAll() shouldHaveSize 1

                    repo.deleteAll()
                    repo.count() shouldBe 0L
                }
            }
        }
    })
