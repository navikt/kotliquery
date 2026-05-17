# Copilot Instructions for KotliQuery

## Project Overview

KotliQuery is a lightweight JDBC wrapper library for Kotlin, forked from [seratch/kotliquery](https://github.com/seratch/kotliquery) and maintained by NAV (Norwegian Labour and Welfare Administration). Published as `no.nav:kotliquery` on GitHub Packages.

### Design Principles

- **Minimal abstraction over JDBC** — we make JDBC easier, not hidden
- **Short learning curve** — simple API surface
- **No breaking changes** — backward compatibility is a hard requirement
- **No unnecessary complexity** — if JDBC already does it well, don't reinvent it

## Tech Stack

- **Language**: Kotlin (2.3.x), JVM toolchain 25
- **Build**: Gradle Kotlin DSL (`build.gradle.kts`)
- **Linting**: ktlint via `org.jlleitschuh.gradle.ktlint` plugin (auto-runs `ktlintFormat` before compilation)
- **Testing**: JUnit 5 with `kotlin-test-junit5`, H2 in-memory database
- **Coverage**: Kover
- **Dependencies**: HikariCP (connection pooling), SLF4J (logging), kotlin-reflect
- **CI**: GitHub Actions, publishing to GitHub Packages (Maven)

## Architecture

All source code lives under `kotliquery` package in `src/main/kotlin/kotliquery/`.

### Core Classes

| Class | Purpose |
|---|---|
| `Session` | Main entry point — wraps a `Connection`, executes queries, handles transactions |
| `TransactionalSession` | Extends `Session` for use inside `session.transaction { tx -> }` blocks |
| `Connection` | Thin wrapper around `java.sql.Connection` with `begin`/`commit`/`rollback` |
| `Query` | Immutable SQL statement with positional or named parameters |
| `Row` | Wraps `ResultSet`, provides typed accessors (`row.int("col")`, `row.stringOrNull("col")`) |
| `Parameter` | Typed parameter for explicit JDBC type binding (useful for nullable params) |
| `SqlValued<T>` | Interface for types that provide their own SQL representation (value classes, enums) |
| `HikariCP` | Convenience singleton for managing named HikariCP DataSource instances |

### Action Classes (`kotliquery/action/`)

Query actions are the bridge between `Query` and `Session`:

- `ExecuteQueryAction` — `query.asExecute` → `Boolean`
- `UpdateQueryAction` — `query.asUpdate` → `Int` (affected rows)
- `UpdateAndReturnGeneratedKeyQueryAction` — `query.asUpdateAndReturnGeneratedKey` → `Long?`
- `ListResultQueryAction<A>` — `query.map(extractor).asList` → `List<A>`
- `NullableResultQueryAction<A>` — `query.map(extractor).asSingle` → `A?`
- `ResultQueryActionBuilder<A>` — intermediate builder from `query.map(extractor)`

### Top-Level Functions (`package.kt`)

- `queryOf(statement, vararg params)` — create Query with positional params
- `queryOf(statement, paramMap)` — create Query with named params (`:name` syntax)
- `sessionOf(url, user, password, ...)` — create Session from JDBC URL
- `sessionOf(dataSource, ...)` — create Session from DataSource
- `using(closeable, block)` — loan pattern for auto-closing resources

## Code Conventions

### Kotlin Style

- Follow **ktlint** defaults — the build auto-formats before compilation
- Use **data classes** for value types (`Query`, `Row`, `Connection`, `Parameter`)
- Use **extension functions** for ergonomic API (`Any?.param<T>()`, `Parameter<T>.sqlType()`)
- Use **trailing lambda** syntax for callbacks (`session.transaction { tx -> }`, `query.map { row -> }`)
- Prefer **explicit types** on public API signatures
- Use `KDoc` comments on public classes; internal functions don't need docs

### Naming Conventions

- Row accessors follow pattern: `type(column)` / `typeOrNull(column)` (e.g., `int("id")` / `intOrNull("id")`)
- Both `columnIndex: Int` (1-based) and `columnLabel: String` overloads for all Row accessors
- Action properties use `as` prefix: `asUpdate`, `asExecute`, `asList`, `asSingle`
- Factory functions use `Of` suffix: `queryOf`, `sessionOf`

### Parameter Handling

- Named params use `:paramName` syntax in SQL strings, parsed via regex `(?<!:):(?!:)[a-zA-Z]\w+`
- The `unwrap()` function in Session handles value classes and `SqlValued<T>` automatically
- Use `Parameter(value, Type::class.java)` or `value.param<Type>()` for explicit JDBC type binding

### Testing Patterns

- Tests use shared `testDataSource` from `test-package.kt` (H2 in-memory via HikariCP, pool size 2)
- `@BeforeEach` creates fresh tables; tests are self-contained
- Helper: `describe(name) { ... }` for organizing assertions within a test
- Helper: `withQueries(vararg stmts) { query -> ... }` for testing multiple SQL statements
- Helper: `String.normalizeSpaces()` for comparing SQL strings
- Use `using(sessionOf(...)) { session -> ... }` pattern in tests

### Patterns to Follow

```kotlin
// Creating and running queries
session.run(queryOf("select ...", param1, param2).map { row -> row.int("col") }.asList)
session.run(queryOf("insert ...", param1).asUpdate)

// Named parameters
queryOf("select ... where name = :name", mapOf("name" to "Alice"))

// Transactions — always block-scoped
session.transaction { tx ->
    tx.run(queryOf(...).asUpdate)
}

// Loan pattern for sessions
using(sessionOf(dataSource)) { session ->
    // work with session
}

// Row mapping function
val toEntity: (Row) -> Entity = { row ->
    Entity(row.int("id"), row.stringOrNull("name"))
}
```

### Patterns to AVOID

- **Do NOT** use `as any` / type-unsafe casts on public API
- **Do NOT** add dependencies without strong justification — this is a minimal library
- **Do NOT** break backward compatibility of existing public API
- **Do NOT** use `begin`/`commit` manually — use `session.transaction { }` block
- **Do NOT** suppress warnings without a clear reason
- **Do NOT** write SQL strings with string interpolation for parameters — always use parameterized queries

## Language

- Code, KDoc, and comments are in **English**
- README and user-facing documentation (`dokumentasjon/`) are in **Norwegian (Bokmål)**
- Commit messages and PR descriptions in English

## Build & Test

```bash
./gradlew build        # Builds, formats (ktlint), tests, generates coverage
./gradlew test         # Run tests only
./gradlew ktlintFormat # Format code
./gradlew ktlintCheck  # Check formatting without fixing
```

Tests require no external database — H2 in-memory is used.
