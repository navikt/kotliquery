package kotliquery.threadlocal

import kotliquery.Connection
import kotliquery.ManagedTransactionContext
import kotliquery.TransactionIsolation
import java.util.IdentityHashMap
import javax.sql.DataSource

/**
 * Manages thread-local transaction binding between [DataSource] instances and their
 * active transactional [Connection].
 *
 * When a [DataSource.transaction][kotliquery.threadlocal.transaction] block is entered,
 * the connection is bound to the current thread. Subsequent calls to
 * [withSession][kotliquery.threadlocal.withSession] on the same [DataSource] automatically
 * join the existing transaction — repositories do not need to receive a Session parameter.
 *
 * Uses [IdentityHashMap] so that binding is based on the exact [DataSource] object reference,
 * not [equals]/[hashCode]. This avoids surprises with wrapped or proxied DataSource instances.
 */
object TransactionManager {
    internal class TransactionContext(
        val connection: Connection,
        val readOnly: Boolean,
        val isolation: TransactionIsolation?,
        val noRollbackFor: Set<Class<out Throwable>>,
    ) : ManagedTransactionContext {
        @Volatile
        override var active: Boolean = true
            internal set
    }

    private val resources = ThreadLocal<IdentityHashMap<DataSource, TransactionContext>>()

    internal fun getResource(dataSource: DataSource): TransactionContext? = resources.get()?.get(dataSource)?.takeIf { it.active }

    internal fun bindResource(
        dataSource: DataSource,
        ctx: TransactionContext,
    ) {
        val map = resources.get() ?: IdentityHashMap<DataSource, TransactionContext>().also { resources.set(it) }
        if (map.containsKey(dataSource)) {
            throw IllegalStateException(
                "A transaction is already active for this DataSource on the current thread. " +
                    "Nested transactions with conflicting options are not supported.",
            )
        }
        map[dataSource] = ctx
    }

    internal fun unbindResource(dataSource: DataSource) {
        val map = resources.get() ?: return
        map.remove(dataSource)?.let { it.active = false }
        if (map.isEmpty()) {
            resources.remove()
        }
    }
}
