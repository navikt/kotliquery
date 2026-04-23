package kotliquery.simple

import kotliquery.Connection
import java.util.IdentityHashMap
import javax.sql.DataSource

/**
 * Thread-local transaction binding between [DataSource] and active [Connection].
 *
 * Uses [IdentityHashMap] so binding is by object identity, not [equals]/[hashCode].
 * This avoids surprises with wrapped or proxied DataSource instances.
 */
internal object TransactionManager {
    private val resources = ThreadLocal<IdentityHashMap<DataSource, Connection>>()

    fun get(dataSource: DataSource): Connection? = resources.get()?.get(dataSource)

    fun bind(
        dataSource: DataSource,
        connection: Connection,
    ) {
        val map = resources.get() ?: IdentityHashMap<DataSource, Connection>().also { resources.set(it) }
        check(!map.containsKey(dataSource)) {
            "A transaction is already active for this DataSource on the current thread."
        }
        map[dataSource] = connection
    }

    fun unbind(dataSource: DataSource) {
        val map = resources.get() ?: return
        map.remove(dataSource)
        if (map.isEmpty()) resources.remove()
    }
}
