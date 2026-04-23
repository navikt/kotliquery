package kotliquery.context

import kotliquery.Connection
import kotliquery.Session
import kotliquery.TransactionIsolation
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import javax.sql.DataSource
import kotlin.reflect.KClass

fun <A> DataSource.withSession(
    returnGeneratedKey: Boolean = false,
    strict: Boolean = false,
    queryTimeout: Int? = null,
    block: context(Session)
    () -> A,
): A = sessionOf(this, returnGeneratedKey, strict, queryTimeout).use { session -> block(session) }

fun <A> DataSource.transaction(
    returnGeneratedKey: Boolean = false,
    strict: Boolean = false,
    queryTimeout: Int? = null,
    readOnly: Boolean = false,
    isolation: TransactionIsolation? = null,
    noRollbackFor: Set<KClass<out Throwable>> = emptySet(),
    block: context(TransactionalSession)
    () -> A,
): A {
    val conn = Connection(this.connection)
    conn.begin(readOnly, isolation)

    val noRollbackForJava = noRollbackFor.mapTo(mutableSetOf()) { it.java }

    try {
        val session = TransactionalSession(conn, returnGeneratedKey, strict = strict, queryTimeout = queryTimeout)
        val result = block(session)
        conn.commit()
        return result
    } catch (e: Throwable) {
        try {
            if (noRollbackForJava.any { it.isInstance(e) }) {
                conn.commit()
            } else {
                conn.rollback()
            }
        } catch (cleanup: Throwable) {
            e.addSuppressed(cleanup)
        }
        throw e
    } finally {
        try {
            conn.close()
        } catch (_: Throwable) {
        }
    }
}
