package kotliquery.dsl

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.testDataSource
import kotliquery.using
import java.time.ZonedDateTime
import java.util.Date

object Members : Table("members") {
    val id = integer("id")
    val name = varchar("name")
    val createdAt = timestamp("created_at")
}

data class Member(
    val id: Int,
    val name: String?,
    val createdAt: ZonedDateTime,
)

private val toMember: (Row) -> Member = { row ->
    Member(row.int("id"), row.stringOrNull("name"), row.zonedDateTime("created_at"))
}

class DslQueryGenerationTest :
    FunSpec({

        test("select all generates SELECT * FROM table") {
            val query = (select(All) from Members).toQuery()
            query.statement shouldBe "SELECT * FROM members"
            query.params shouldHaveSize 0
        }

        test("select specific columns generates correct SQL") {
            val query = (select(Members.id, Members.name) from Members).toQuery()
            query.statement shouldBe "SELECT id, name FROM members"
        }

        test("where with eq generates parameterised WHERE clause") {
            val query = (select(All) from Members where (Members.name eq "Alice")).toQuery()
            query.statement shouldBe "SELECT * FROM members WHERE name = ?"
            query.params shouldContainExactly listOf("Alice")
        }

        test("where with neq") {
            val query = (select(All) from Members where (Members.name neq "Bob")).toQuery()
            query.statement shouldBe "SELECT * FROM members WHERE name != ?"
            query.params shouldContainExactly listOf("Bob")
        }

        test("compound condition with and") {
            val query =
                (select(All) from Members where ((Members.name eq "Alice") and (Members.id gt 5))).toQuery()
            query.statement shouldBe "SELECT * FROM members WHERE (name = ? AND id > ?)"
            query.params shouldContainExactly listOf("Alice", 5)
        }

        test("compound condition with or") {
            val query =
                (select(All) from Members where ((Members.name eq "Alice") or (Members.name eq "Bob"))).toQuery()
            query.statement shouldBe "SELECT * FROM members WHERE (name = ? OR name = ?)"
            query.params shouldContainExactly listOf("Alice", "Bob")
        }

        test("comparison operators gt, lt, gte, lte") {
            (select(All) from Members where (Members.id gt 1)).toQuery().statement shouldBe
                "SELECT * FROM members WHERE id > ?"
            (select(All) from Members where (Members.id lt 10)).toQuery().statement shouldBe
                "SELECT * FROM members WHERE id < ?"
            (select(All) from Members where (Members.id gte 1)).toQuery().statement shouldBe
                "SELECT * FROM members WHERE id >= ?"
            (select(All) from Members where (Members.id lte 10)).toQuery().statement shouldBe
                "SELECT * FROM members WHERE id <= ?"
        }

        test("like operator") {
            val query = (select(All) from Members where (Members.name like "%Ali%")).toQuery()
            query.statement shouldBe "SELECT * FROM members WHERE name LIKE ?"
            query.params shouldContainExactly listOf("%Ali%")
        }

        test("isNull and isNotNull") {
            (select(All) from Members where Members.name.isNull()).toQuery().statement shouldBe
                "SELECT * FROM members WHERE name IS NULL"
            (select(All) from Members where Members.name.isNotNull()).toQuery().statement shouldBe
                "SELECT * FROM members WHERE name IS NOT NULL"
        }

        test("in operator via within") {
            val query = (select(All) from Members where (Members.id within listOf(1, 2, 3))).toQuery()
            query.statement shouldBe "SELECT * FROM members WHERE id IN (?, ?, ?)"
            query.params shouldContainExactly listOf(1, 2, 3)
        }

        test("between operator") {
            val query = (select(All) from Members where (Members.id between 1..10)).toQuery()
            query.statement shouldBe "SELECT * FROM members WHERE id BETWEEN ? AND ?"
            query.params shouldContainExactly listOf(1, 10)
        }

        test("not negates condition") {
            val query = (select(All) from Members where not(Members.name eq "Alice")).toQuery()
            query.statement shouldBe "SELECT * FROM members WHERE NOT (name = ?)"
            query.params shouldContainExactly listOf("Alice")
        }

        test("orderBy and limit") {
            val query =
                (select(All) from Members orderBy Members.name.asc() orderBy Members.id.desc() limit 5).toQuery()
            query.statement shouldBe "SELECT * FROM members ORDER BY name ASC, id DESC LIMIT 5"
        }

        test("insert generates correct SQL with params") {
            val query = insertInto(Members).values(Members.name to "Alice", Members.createdAt to Date()).toQuery()
            query.statement shouldBe "INSERT INTO members (name, created_at) VALUES (?, ?)"
            query.params shouldHaveSize 2
        }

        test("insert infix single assignment") {
            val query = (insertInto(Members) values (Members.name to "Bob")).toQuery()
            query.statement shouldBe "INSERT INTO members (name) VALUES (?)"
            query.params shouldContainExactly listOf("Bob")
        }

        test("update with where generates correct SQL") {
            val query =
                (update(Members).set(Members.name to "Bob") where (Members.id eq 1)).toQuery()
            query.statement shouldBe "UPDATE members SET name = ? WHERE id = ?"
            query.params shouldContainExactly listOf("Bob", 1)
        }

        test("update infix single set") {
            val query =
                (update(Members) set (Members.name to "Carol") where (Members.id eq 2)).toQuery()
            query.statement shouldBe "UPDATE members SET name = ? WHERE id = ?"
            query.params shouldContainExactly listOf("Carol", 2)
        }

        test("delete with where generates correct SQL") {
            val query = (deleteFrom(Members) where (Members.id eq 1)).toQuery()
            query.statement shouldBe "DELETE FROM members WHERE id = ?"
            query.params shouldContainExactly listOf(1)
        }

        test("table columns are tracked") {
            Members.columns shouldHaveSize 3
            Members.columns.map { it.name } shouldContainExactly listOf("id", "name", "created_at")
        }
    })

class DslRoundTripTest :
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

        test("insert and select all round-trip") {
            using(sessionOf(testDataSource)) { session ->
                val now = Date()
                session.run(insertInto(Members).values(Members.name to "Alice", Members.createdAt to now).asUpdate)
                session.run(insertInto(Members).values(Members.name to "Bob", Members.createdAt to now).asUpdate)

                val members = session.run((select(All) from Members).map(toMember).asList)
                members shouldHaveSize 2
                members.map { it.name } shouldContainExactly listOf("Alice", "Bob")
            }
        }

        test("select with where filters correctly") {
            using(sessionOf(testDataSource)) { session ->
                val now = Date()
                session.run(insertInto(Members).values(Members.name to "Alice", Members.createdAt to now).asUpdate)
                session.run(insertInto(Members).values(Members.name to "Bob", Members.createdAt to now).asUpdate)

                val alice = session.run((select(All) from Members where (Members.name eq "Alice")).map(toMember).asSingle)
                alice.shouldNotBeNull()
                alice.name shouldBe "Alice"

                val nobody = session.run((select(All) from Members where (Members.name eq "Nobody")).map(toMember).asSingle)
                nobody.shouldBeNull()
            }
        }

        test("select specific columns") {
            using(sessionOf(testDataSource)) { session ->
                val now = Date()
                session.run(insertInto(Members).values(Members.name to "Alice", Members.createdAt to now).asUpdate)

                val names =
                    session.run(
                        (select(Members.name) from Members).map { row -> row.stringOrNull("name") }.asList,
                    )
                names shouldContainExactly listOf("Alice")
            }
        }

        test("update modifies rows") {
            using(sessionOf(testDataSource)) { session ->
                val now = Date()
                session.run(insertInto(Members).values(Members.name to "Alice", Members.createdAt to now).asUpdate)

                val affected =
                    session.run((update(Members) set (Members.name to "Alicia") where (Members.name eq "Alice")).asUpdate)
                affected shouldBe 1

                val updated = session.run((select(All) from Members).map(toMember).asSingle)
                updated.shouldNotBeNull()
                updated.name shouldBe "Alicia"
            }
        }

        test("delete removes rows") {
            using(sessionOf(testDataSource)) { session ->
                val now = Date()
                session.run(insertInto(Members).values(Members.name to "Alice", Members.createdAt to now).asUpdate)
                session.run(insertInto(Members).values(Members.name to "Bob", Members.createdAt to now).asUpdate)

                session.run((deleteFrom(Members) where (Members.name eq "Alice")).asUpdate)

                val remaining = session.run((select(All) from Members).map(toMember).asList)
                remaining shouldHaveSize 1
                remaining.first().name shouldBe "Bob"
            }
        }

        test("compound where with and") {
            using(sessionOf(testDataSource)) { session ->
                val now = Date()
                session.run(insertInto(Members).values(Members.name to "Alice", Members.createdAt to now).asUpdate)
                session.run(insertInto(Members).values(Members.name to "Bob", Members.createdAt to now).asUpdate)

                val result =
                    session.run(
                        (select(All) from Members where ((Members.name eq "Alice") and (Members.id gte 1)))
                            .map(toMember)
                            .asList,
                    )
                result shouldHaveSize 1
                result.first().name shouldBe "Alice"
            }
        }

        test("orderBy and limit") {
            using(sessionOf(testDataSource)) { session ->
                val now = Date()
                session.run(insertInto(Members).values(Members.name to "Charlie", Members.createdAt to now).asUpdate)
                session.run(insertInto(Members).values(Members.name to "Alice", Members.createdAt to now).asUpdate)
                session.run(insertInto(Members).values(Members.name to "Bob", Members.createdAt to now).asUpdate)

                val first =
                    session.run(
                        (select(All) from Members orderBy Members.name.asc() limit 1)
                            .map(toMember)
                            .asList,
                    )
                first shouldHaveSize 1
                first.first().name shouldBe "Alice"
            }
        }

        test("like pattern matching") {
            using(sessionOf(testDataSource)) { session ->
                val now = Date()
                session.run(insertInto(Members).values(Members.name to "Alice", Members.createdAt to now).asUpdate)
                session.run(insertInto(Members).values(Members.name to "Alicia", Members.createdAt to now).asUpdate)
                session.run(insertInto(Members).values(Members.name to "Bob", Members.createdAt to now).asUpdate)

                val aliNames =
                    session.run(
                        (select(All) from Members where (Members.name like "Ali%"))
                            .map(toMember)
                            .asList,
                    )
                aliNames shouldHaveSize 2
            }
        }

        test("within (IN) filter") {
            using(sessionOf(testDataSource)) { session ->
                val now = Date()
                session.run(insertInto(Members).values(Members.name to "Alice", Members.createdAt to now).asUpdate)
                session.run(insertInto(Members).values(Members.name to "Bob", Members.createdAt to now).asUpdate)
                session.run(insertInto(Members).values(Members.name to "Charlie", Members.createdAt to now).asUpdate)

                val result =
                    session.run(
                        (select(All) from Members where (Members.name within listOf("Alice", "Charlie")))
                            .map(toMember)
                            .asList,
                    )
                result shouldHaveSize 2
                result.map { it.name }.toSet() shouldBe setOf("Alice", "Charlie")
            }
        }

        test("DSL queries integrate with session.transaction") {
            using(sessionOf(testDataSource)) { session ->
                val now = Date()
                session.transaction { tx ->
                    tx.run(insertInto(Members).values(Members.name to "Alice", Members.createdAt to now).asUpdate)
                    tx.run(insertInto(Members).values(Members.name to "Bob", Members.createdAt to now).asUpdate)
                }

                val count = session.run((select(All) from Members).map(toMember).asList)
                count shouldHaveSize 2
            }
        }
    })
