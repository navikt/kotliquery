package kotliquery

enum class TransactionIsolation(
    val level: Int,
) {
    READ_UNCOMMITTED(java.sql.Connection.TRANSACTION_READ_UNCOMMITTED),
    READ_COMMITTED(java.sql.Connection.TRANSACTION_READ_COMMITTED),
    REPEATABLE_READ(java.sql.Connection.TRANSACTION_REPEATABLE_READ),
    SERIALIZABLE(java.sql.Connection.TRANSACTION_SERIALIZABLE),
}
