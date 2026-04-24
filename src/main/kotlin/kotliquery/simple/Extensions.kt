package kotliquery.simple

import kotliquery.Connection
import kotliquery.Session
import kotliquery.TransactionIsolation
import kotliquery.TransactionalSession
import kotliquery.sessionOf
import javax.sql.DataSource

fun <A> DataSource.withSession(
    returnGeneratedKey: Boolean = false,
    strict: Boolean = false,
    queryTimeout: Int? = null,
    block: Session.() -> A,
): A {
    val existing = TransactionManager.get(this)
    if (existing != null) {
        return Session(existing, returnGeneratedKey, strict = strict, queryTimeout = queryTimeout).block()
    }
    return sessionOf(this, returnGeneratedKey, strict, queryTimeout).use(block)
}

fun <A> DataSource.transaction(
    returnGeneratedKey: Boolean = false,
    strict: Boolean = false,
    queryTimeout: Int? = null,
    readOnly: Boolean = false,
    isolation: TransactionIsolation? = null,
    block: TransactionalSession.() -> A,
): A {
    val existing = TransactionManager.get(this)
    if (existing != null) {
        return TransactionalSession(existing, returnGeneratedKey, strict = strict, queryTimeout = queryTimeout).block()
    }

    val conn = Connection(this.connection)
    conn.begin(readOnly, isolation)
    TransactionManager.bind(this, conn)

    try {
        val result = TransactionalSession(conn, returnGeneratedKey, strict = strict, queryTimeout = queryTimeout).block()
        conn.commit()
        return result
    } catch (e: Throwable) {
        try {
            conn.rollback()
        } catch (cleanup: Throwable) {
            e.addSuppressed(cleanup)
        }
        throw e
    } finally {
        TransactionManager.unbind(this)
        try {
            conn.close()
        } catch (_: Throwable) {
        }
    }
}
