# AGENTS.md — Tests (`src/test/kotlin/kotliquery/`)

## Test Infrastructure

- **Framework**: JUnit 5 + `kotlin-test-junit5` (provides `assertEquals`, `assertNull`, `assertNotNull`, `assertFailsWith`)
- **Database**: H2 in-memory — no external DB needed, tests are fully self-contained
- **Connection pool**: Shared `testDataSource` from `test-package.kt` — HikariCP with `maximumPoolSize=2`

## Test Files

| File | Purpose | Lines |
|---|---|---|
| `UsageTest.kt` | End-to-end: sessions, actions, transactions, batch, strict mode, query timeout, HikariCP | ~566 |
| `TransactionSharingTest.kt` | Transparent transactions: thread-local sharing, nested calls, readOnly/isolation propagation, suppressed exceptions | ~200 |
| `NamedParamTest.kt` | Named parameter regex parsing: extraction, repeated params, edge cases (timestamps, `::` casts) | ~73 |
| `DataTypesTest.kt` | Round-trip for all supported types: temporal, UUID, value classes, `SqlValued` enums | ~259 |
| `test-package.kt` | Shared test infrastructure: `testDataSource`, helpers | ~32 |

## Shared Helpers (`test-package.kt`)

```kotlin
// Shared H2 connection pool (maximumPoolSize=2)
val testDataSource = HikariCP.init(url="jdbc:h2:mem:default", username="user", password="pass") { maximumPoolSize = 2 }

// Label a group of assertions within a test
fun describe(description: String, tests: () -> Unit)

// Run assertions against multiple SQL statement strings
fun withQueries(vararg stmts: String, assertions: (Query) -> Unit)

// Collapse whitespace for SQL comparison
fun String.normalizeSpaces(): String
```

## Conventions

### Test Structure

- Each test class uses `@BeforeEach` to create/reset tables — tests don't depend on each other
- Session-based tests use `using(sessionOf(testDataSource)) { session -> ... }` pattern
- `describe("label") { ... }` groups related assertions within a single `@Test` method
- No mocking — everything hits real H2 through actual JDBC

### Table Setup Pattern

```kotlin
@BeforeEach
fun before() {
    using(sessionOf(testDataSource)) { session ->
        session.execute(queryOf("drop table members if exists"))
        session.execute(queryOf("""
            create table members (
                id serial primary key,
                name varchar(64),
                created_at timestamp not null
            )
        """.trimIndent()))
    }
}
```

### Data Type Round-Trip Pattern (DataTypesTest)

```kotlin
private fun insertAndAssert(
    tstz: Any? = null, ts: Any? = null, /* ... */
    assertRowFn: (Row) -> Unit
) {
    sessionOf(testDataSource).use { session ->
        session.execute(queryOf("truncate table session_test;"))
        session.execute(queryOf("insert ...", mapOf("tstz" to tstz, /* ... */)))
        session.single(queryOf("select * from session_test"), assertRowFn)
    }
}

// Usage:
@Test fun testInstant() {
    val value = Instant.parse("2010-01-01T10:00:00.123456Z")
    insertAndAssert(tstz = value) { row ->
        assertEquals(value, row.instant("val_tstz"))
    }
}
```

### Named Param Testing Pattern (NamedParamTest)

```kotlin
describe("should extract a single param") {
    withQueries(
        "SELECT * FROM table t WHERE t.a = :param",
    ) { query ->
        assertEquals("SELECT * FROM table t WHERE t.a = ?", query.cleanStatement)
        assertEquals(mapOf("param" to listOf(0)), query.replacementMap)
    }
}
```

### PreparedStatement Assertion Pattern (UsageTest)

For testing parameter binding, `UsageTest` inspects the H2 `PreparedStatement.toString()` output:

```kotlin
private fun withPreparedStmt(query: Query, closure: (PreparedStatement) -> Unit) {
    using(sessionOf(testDataSource)) { session ->
        closure(session.createPreparedStatement(query))
    }
}

// H2's toString() format: "prep123: SELECT ... {1: 'value', 2: 42}"
fun String.extractQueryFromPreparedStmt() = this.replace(Regex("^.*?: "), "").normalizeSpaces()
```

## What to Test When

| Change | Test file to update |
|---|---|
| New Row accessor type | `DataTypesTest.kt` — add round-trip test |
| New `Session.setParam` branch | `DataTypesTest.kt` — add round-trip test |
| Named param regex change | `NamedParamTest.kt` — add edge case |
| New action type | `UsageTest.kt` — add end-to-end usage |
| Session constructor param | `UsageTest.kt` — add feature test |
| Batch operations | `UsageTest.kt` — add batch test |
| `SqlValued` / value class | `DataTypesTest.kt` — add enum/value class test |

## Edge Cases Covered

- Null parameters (untyped and typed via `Parameter`)
- Repeated named parameters (`:param` appearing multiple times)
- Timestamp strings not confused as named params (`'2018-01-01 00:00:00'`)
- PostgreSQL `::` cast not confused as named params (`'2018-01-01'::DATE`)
- Strict mode throwing on duplicate rows
- Transaction rollback on exception
- Transparent transaction sharing via thread-local DataSource binding
- Nested `transaction` / `withSession` / `sessionOf` calls in same thread
- Transaction settings (readOnly, isolation) propagation
- Preserving original exceptions when cleanup fails (suppressed exceptions)
- Transaction inside coroutine scope (`runBlocking { session.transaction { } }`)
- Query timeout propagation to PreparedStatement
- Value class unwrapping (Long, String)
- `SqlValued` enum unwrapping (String-valued, Long-valued)
