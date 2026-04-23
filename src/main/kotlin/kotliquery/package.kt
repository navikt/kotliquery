package kotliquery

import java.sql.DriverManager
import javax.sql.DataSource
import kotlin.reflect.KClass

fun queryOf(
    statement: String,
    vararg params: Any?,
): Query = Query(statement, params = params.toList())

fun queryOf(
    statement: String,
    paramMap: Map<String, Any?>,
): Query = Query(statement, paramMap = paramMap)

fun sessionOf(
    url: String,
    user: String,
    password: String,
    returnGeneratedKey: Boolean = false,
    strict: Boolean = false,
    queryTimeout: Int? = null,
): Session {
    val conn = DriverManager.getConnection(url, user, password)
    return Session(Connection(conn), returnGeneratedKey, strict = strict, queryTimeout = queryTimeout)
}

fun sessionOf(
    dataSource: DataSource,
    returnGeneratedKey: Boolean = false,
    strict: Boolean = false,
    queryTimeout: Int? = null,
): Session {
    val ctx = TransactionManager.getResource(dataSource)
    if (ctx != null) {
        return Session(ctx.connection, returnGeneratedKey, strict = strict, queryTimeout = queryTimeout).also {
            it.transactionContext = ctx
        }
    }
    return Session(Connection(dataSource.connection), returnGeneratedKey, strict = strict, queryTimeout = queryTimeout)
}

fun <A : AutoCloseable, R> using(
    closeable: A?,
    f: (A) -> R,
): R = LoanPattern.using(closeable, f)

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
