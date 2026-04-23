package kotliquery.threadlocal

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
    block: Session.() -> A,
): A {
    val ctx = TransactionManager.getResource(this)
    if (ctx != null) {
        val session = Session(ctx.connection, returnGeneratedKey, strict = strict, queryTimeout = queryTimeout)
        session.transactionContext = ctx
        return session.use(block)
    }
    return sessionOf(this, returnGeneratedKey, strict, queryTimeout).use(block)
}

fun <A> DataSource.transaction(
    returnGeneratedKey: Boolean = false,
    strict: Boolean = false,
    queryTimeout: Int? = null,
    readOnly: Boolean = false,
    isolation: TransactionIsolation? = null,
    noRollbackFor: Set<KClass<out Throwable>> = emptySet(),
    block: TransactionalSession.() -> A,
): A {
    val existing = TransactionManager.getResource(this)
    if (existing != null) {
        val session =
            TransactionalSession(
                existing.connection,
                returnGeneratedKey,
                strict = strict,
                queryTimeout = queryTimeout,
            )
        session.transactionContext = existing
        return session.block()
    }

    val conn = Connection(this.connection)
    conn.begin(readOnly, isolation)

    val noRollbackForJava = noRollbackFor.mapTo(mutableSetOf()) { it.java }
    val ctx = TransactionManager.TransactionContext(conn, readOnly, isolation, noRollbackForJava)
    TransactionManager.bindResource(this, ctx)

    try {
        val session = TransactionalSession(conn, returnGeneratedKey, strict = strict, queryTimeout = queryTimeout)
        val result = session.block()
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
        TransactionManager.unbindResource(this)
        try {
            conn.close()
        } catch (_: Throwable) {
        }
    }
}
