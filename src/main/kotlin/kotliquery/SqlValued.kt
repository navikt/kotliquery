package kotliquery

interface SqlValued<T> {
    fun sqlValue(): T
}
