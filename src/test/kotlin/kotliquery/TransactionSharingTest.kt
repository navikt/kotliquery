package kotliquery

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.Date

class TransactionSharingTest :
    FunSpec({

        val insert = "insert into members (name, created_at) values (?, ?)"
        val countMembers = queryOf("select count(*) as cnt from members").map { it.int("cnt") }.asSingle
        val countAuditLog = queryOf("select count(*) as cnt from audit_log").map { it.int("cnt") }.asSingle

        beforeEach {
            using(sessionOf(testDataSource)) { session ->
                session.execute(queryOf("drop table members if exists"))
                session.execute(queryOf("drop table audit_log if exists"))
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
                session.execute(
                    queryOf(
                        """
                        create table audit_log (
                            id serial primary key,
                            message varchar(255)
                        )
                        """.trimIndent(),
                    ),
                )
            }
        }

        context("withSession") {
            test("executes queries and closes session") {
                testDataSource.withSession {
                    run(queryOf(insert, "Alice", Date()).asUpdate)
                    run(queryOf(insert, "Bob", Date()).asUpdate)
                }

                testDataSource.withSession { run(countMembers) } shouldBe 2
            }

            test("returns value from block") {
                val ids =
                    testDataSource.withSession {
                        run(queryOf(insert, "Alice", Date()).asUpdate)
                        run(queryOf(insert, "Bob", Date()).asUpdate)
                        run(queryOf("select id from members").map { it.int("id") }.asList)
                    }

                ids.size shouldBe 2
            }
        }

        context("DataSource.transaction") {
            test("commits on success") {
                testDataSource.transaction {
                    run(queryOf(insert, "Alice", Date()).asUpdate)
                    run(queryOf(insert, "Bob", Date()).asUpdate)
                }

                testDataSource.withSession { run(countMembers) } shouldBe 2
            }

            test("rolls back on exception") {
                shouldThrow<RuntimeException> {
                    testDataSource.transaction {
                        run(queryOf(insert, "Alice", Date()).asUpdate)
                        throw RuntimeException("rollback")
                    }
                }

                testDataSource.withSession { run(countMembers) } shouldBe 0
            }

            test("returns value from block") {
                val id =
                    testDataSource.transaction {
                        run(queryOf(insert, "Alice", Date()).asUpdate)
                        run(queryOf("select id from members where name = ?", "Alice").map { it.int("id") }.asSingle)
                    }

                id shouldBe 1
            }
        }

        context("backward compatibility") {
            test("Session.transaction still works with parameter style") {
                using(sessionOf(testDataSource)) { session ->
                    session.transaction { tx ->
                        tx.run(queryOf(insert, "Alice", Date()).asUpdate)
                    }

                    session.run(countMembers) shouldBe 1
                }
            }
        }

        context("cross-class transaction sharing") {
            val memberRepo = MemberRepository()
            val auditRepo = AuditRepository()

            test("multiple repositories commit in same transaction") {
                testDataSource.transaction {
                    memberRepo.insert(this, "Alice")
                    auditRepo.log(this, "inserted Alice")
                }

                testDataSource.withSession {
                    run(countMembers) shouldBe 1
                    run(countAuditLog) shouldBe 1
                }
            }

            test("multiple repositories roll back together") {
                shouldThrow<RuntimeException> {
                    testDataSource.transaction {
                        memberRepo.insert(this, "Alice")
                        auditRepo.log(this, "inserted Alice")
                        throw RuntimeException("rollback everything")
                    }
                }

                testDataSource.withSession {
                    run(countMembers) shouldBe 0
                    run(countAuditLog) shouldBe 0
                }
            }
        }
    })

private class MemberRepository {
    fun insert(
        session: Session,
        name: String,
    ): Int = session.run(queryOf("insert into members (name, created_at) values (?, ?)", name, Date()).asUpdate)
}

private class AuditRepository {
    fun log(
        session: Session,
        message: String,
    ): Int = session.run(queryOf("insert into audit_log (message) values (?)", message).asUpdate)
}
