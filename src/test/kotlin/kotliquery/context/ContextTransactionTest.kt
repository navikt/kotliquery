package kotliquery.context

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotliquery.Session
import kotliquery.TransactionIsolation
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.testDataSource
import kotliquery.using
import java.util.Date

class ContextTransactionTest :
    FunSpec({

        val insert = "insert into members (name, created_at) values (?, ?)"

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
            test("executes queries via context parameter") {
                testDataSource.withSession {
                    insertMember("Alice")
                    insertMember("Bob")
                }

                using(sessionOf(testDataSource)) { s ->
                    s.run(countMembersQuery) shouldBe 2
                }
            }

            test("returns value from block") {
                val count =
                    testDataSource.withSession {
                        insertMember("Alice")
                        insertMember("Bob")
                        countMembers()
                    }

                count shouldBe 2
            }
        }

        context("DataSource.transaction") {
            test("commits on success") {
                testDataSource.transaction {
                    insertMember("Alice")
                    insertMember("Bob")
                }

                using(sessionOf(testDataSource)) { s ->
                    s.run(countMembersQuery) shouldBe 2
                }
            }

            test("rolls back on exception") {
                shouldThrow<RuntimeException> {
                    testDataSource.transaction {
                        insertMember("Alice")
                        throw RuntimeException("rollback")
                    }
                }

                using(sessionOf(testDataSource)) { s ->
                    s.run(countMembersQuery) shouldBe 0
                }
            }

            test("returns value from block") {
                val id =
                    testDataSource.transaction {
                        insertMember("Alice")
                        memberIdByName("Alice")
                    }

                id shouldBe 1
            }
        }

        context("context parameter sharing") {
            test("multiple context functions share transaction") {
                testDataSource.transaction {
                    insertMember("Alice")
                    insertAuditLog("inserted Alice")
                }

                using(sessionOf(testDataSource)) { s ->
                    s.run(countMembersQuery) shouldBe 1
                    s.run(countAuditLogQuery) shouldBe 1
                }
            }

            test("all operations roll back together") {
                shouldThrow<RuntimeException> {
                    testDataSource.transaction {
                        insertMember("Alice")
                        insertAuditLog("inserted Alice")
                        throw RuntimeException("rollback everything")
                    }
                }

                using(sessionOf(testDataSource)) { s ->
                    s.run(countMembersQuery) shouldBe 0
                    s.run(countAuditLogQuery) shouldBe 0
                }
            }

            test("repository classes share transaction via context") {
                val memberRepo = ContextMemberRepository()
                val auditRepo = ContextAuditRepository()

                testDataSource.transaction {
                    memberRepo.insert("Alice")
                    auditRepo.log("inserted Alice")
                }

                using(sessionOf(testDataSource)) { s ->
                    s.run(countMembersQuery) shouldBe 1
                    s.run(countAuditLogQuery) shouldBe 1
                }
            }

            test("repositories work outside a transaction too") {
                val memberRepo = ContextMemberRepository()

                testDataSource.withSession {
                    memberRepo.insert("Alice")
                }

                using(sessionOf(testDataSource)) { s ->
                    s.run(countMembersQuery) shouldBe 1
                }
            }
        }

        context("backward compatibility") {
            test("Session.transaction still works with parameter style") {
                using(sessionOf(testDataSource)) { session ->
                    session.transaction { tx ->
                        tx.run(queryOf(insert, "Alice", Date()).asUpdate)
                    }

                    session.run(countMembersQuery) shouldBe 1
                }
            }
        }

        context("explicit session passing still works") {
            val memberRepo = ExplicitMemberRepository()
            val auditRepo = ExplicitAuditRepository()

            test("multiple repositories commit in same transaction") {
                testDataSource.transaction {
                    memberRepo.insert(currentSession(), "Alice")
                    auditRepo.log(currentSession(), "inserted Alice")
                }

                using(sessionOf(testDataSource)) { s ->
                    s.run(countMembersQuery) shouldBe 1
                    s.run(countAuditLogQuery) shouldBe 1
                }
            }

            test("multiple repositories roll back together") {
                shouldThrow<RuntimeException> {
                    testDataSource.transaction {
                        memberRepo.insert(currentSession(), "Alice")
                        auditRepo.log(currentSession(), "inserted Alice")
                        throw RuntimeException("rollback everything")
                    }
                }

                using(sessionOf(testDataSource)) { s ->
                    s.run(countMembersQuery) shouldBe 0
                    s.run(countAuditLogQuery) shouldBe 0
                }
            }
        }

        context("transaction options") {
            test("readOnly transaction allows reads") {
                using(sessionOf(testDataSource)) { s ->
                    s.run(queryOf(insert, "Alice", Date()).asUpdate)
                }

                val count =
                    testDataSource.transaction(readOnly = true) {
                        countMembers()
                    }

                count shouldBe 1
            }

            test("isolation level is applied") {
                testDataSource.transaction(isolation = TransactionIsolation.SERIALIZABLE) {
                    insertMember("Alice")
                }

                using(sessionOf(testDataSource)) { s ->
                    s.run(countMembersQuery) shouldBe 1
                }
            }
        }

        context("rollback rules") {
            test("noRollbackFor commits on matching exception") {
                shouldThrow<CustomBusinessException> {
                    testDataSource.transaction(noRollbackFor = setOf(CustomBusinessException::class)) {
                        insertMember("Alice")
                        throw CustomBusinessException("business rule violation")
                    }
                }

                using(sessionOf(testDataSource)) { s ->
                    s.run(countMembersQuery) shouldBe 1
                }
            }

            test("noRollbackFor still rolls back on non-matching exception") {
                shouldThrow<RuntimeException> {
                    testDataSource.transaction(noRollbackFor = setOf(CustomBusinessException::class)) {
                        insertMember("Alice")
                        throw RuntimeException("unexpected error")
                    }
                }

                using(sessionOf(testDataSource)) { s ->
                    s.run(countMembersQuery) shouldBe 0
                }
            }

            test("noRollbackFor matches subclasses") {
                shouldThrow<SpecificBusinessException> {
                    testDataSource.transaction(noRollbackFor = setOf(CustomBusinessException::class)) {
                        insertMember("Alice")
                        throw SpecificBusinessException("specific violation")
                    }
                }

                using(sessionOf(testDataSource)) { s ->
                    s.run(countMembersQuery) shouldBe 1
                }
            }
        }
    })

private val countMembersQuery = queryOf("select count(*) as cnt from members").map { it.int("cnt") }.asSingle

private val countAuditLogQuery = queryOf("select count(*) as cnt from audit_log").map { it.int("cnt") }.asSingle

context(session: Session)
private fun insertMember(name: String): Int =
    session.run(queryOf("insert into members (name, created_at) values (?, ?)", name, Date()).asUpdate)

context(session: Session)
private fun insertAuditLog(message: String): Int =
    session.run(queryOf("insert into audit_log (message) values (?)", message).asUpdate)

context(session: Session)
private fun countMembers(): Int? =
    session.run(countMembersQuery)

context(session: Session)
private fun memberIdByName(name: String): Int? =
    session.run(queryOf("select id from members where name = ?", name).map { it.int("id") }.asSingle)

context(session: Session)
private fun currentSession(): Session = session

private class ContextMemberRepository {
    context(session: Session)
    fun insert(name: String): Int =
        session.run(queryOf("insert into members (name, created_at) values (?, ?)", name, Date()).asUpdate)
}

private class ContextAuditRepository {
    context(session: Session)
    fun log(message: String): Int =
        session.run(queryOf("insert into audit_log (message) values (?)", message).asUpdate)
}

private class ExplicitMemberRepository {
    fun insert(
        session: Session,
        name: String,
    ): Int = session.run(queryOf("insert into members (name, created_at) values (?, ?)", name, Date()).asUpdate)
}

private class ExplicitAuditRepository {
    fun log(
        session: Session,
        message: String,
    ): Int = session.run(queryOf("insert into audit_log (message) values (?)", message).asUpdate)
}

private open class CustomBusinessException(
    message: String,
) : RuntimeException(message)

private class SpecificBusinessException(
    message: String,
) : CustomBusinessException(message)
