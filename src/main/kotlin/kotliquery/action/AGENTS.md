# AGENTS.md — Action Types (`src/main/kotlin/kotliquery/action/`)

## Purpose

Actions are the bridge between `Query` and `Session`. They encode **what to do** with a query (execute, update, select-list, select-single) as a type, so `Session.run()` can dispatch via overloading.

## Architecture

```
QueryAction<A>                    ← interface: runWithSession(Session): A
├── ExecuteQueryAction            ← query.asExecute     → Boolean
├── UpdateQueryAction             ← query.asUpdate      → Int
├── UpdateAndReturnGeneratedKeyQueryAction  ← query.asUpdateAndReturnGeneratedKey → Long?
├── ListResultQueryAction<A>      ← query.map(fn).asList   → List<A>
└── NullableResultQueryAction<A>  ← query.map(fn).asSingle → A?

ResultQueryActionBuilder<A>       ← query.map(fn) — intermediate, not a QueryAction
├── .asList  → ListResultQueryAction<A>
└── .asSingle → NullableResultQueryAction<A>
```

## File Details

### QueryAction.kt (interface)

```kotlin
interface QueryAction<A> {
    fun runWithSession(session: Session): A
}
```

Single-method interface. Every concrete action delegates to the corresponding `Session` method.

### ExecuteQueryAction.kt

- Wraps `Query`, delegates to `session.execute(query)` → `Boolean`
- Used for DDL, or any statement where you only care about success/failure

### UpdateQueryAction.kt

- Wraps `Query`, delegates to `session.update(query)` → `Int`
- Returns affected row count. Used for INSERT/UPDATE/DELETE.

### UpdateAndReturnGeneratedKeyQueryAction.kt

- Wraps `Query`, delegates to `session.updateAndReturnGeneratedKey(query)` → `Long?`
- Used for INSERT with auto-generated keys. Requires `returnGeneratedKeys=true` on Session.

### ListResultQueryAction.kt

- Wraps `Query` + `extractor: (Row) -> A?`, delegates to `session.list(query, extractor)` → `List<A>`
- Null extractor results are filtered out.

### NullableResultQueryAction.kt

- Wraps `Query` + `extractor: (Row) -> A?`, delegates to `session.single(query, extractor)` → `A?`
- In `strict` mode: throws if >1 row returned.

### ResultQueryActionBuilder.kt

- Not a `QueryAction` — it's a builder created by `query.map(extractor)`
- Has two lazy properties: `.asList` → `ListResultQueryAction`, `.asSingle` → `NullableResultQueryAction`

## Pattern: Adding a New Action Type

If you need a new execution mode:

1. Create a data class implementing `QueryAction<YourReturnType>`
2. Implement `runWithSession(session)` delegating to a Session method
3. Add the corresponding Session method if it doesn't exist
4. Add a `session.run(action: YourAction)` overload in `Session.kt`
5. Add a lazy property on `Query` (e.g., `val asYourAction by lazy { ... }`) if it doesn't need an extractor
6. Or add it to `ResultQueryActionBuilder` if it needs an extractor

## Conventions

- All action classes are **data classes** (except the interface)
- All are **immutable** — constructed once, run many times
- Construction is **lazy** (via `by lazy` on Query properties) — no allocation until first use
- Each action does ONE thing — delegates to exactly one Session method
