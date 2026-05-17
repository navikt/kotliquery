# AGENTS.md — KotliQuery

## Project Identity

KotliQuery is a lightweight Kotlin JDBC wrapper library maintained by NAV (navikt). Fork of [seratch/kotliquery](https://github.com/seratch/kotliquery), published as `no.nav:kotliquery` on GitHub Packages.

**Repository**: `navikt/kotliquery`
**Package**: `no.nav:kotliquery`
**IDE**: JetBrains IntelliJ IDEA

## Core Design Constraints (HARD RULES)

1. **Backward compatibility is sacred** — never break existing public API
2. **Minimal abstraction** — wrap JDBC, don't hide it
3. **No unnecessary dependencies** — every dep must be justified
4. **Short learning curve** — API should be intuitive to any Kotlin/JDBC developer

## Tech Stack

| Aspect | Technology |
|---|---|
| Language | Kotlin 2.3.x |
| JVM | Toolchain 25 |
| Build | Gradle Kotlin DSL |
| Lint | ktlint (auto-formats on compile via `dependsOn("ktlintFormat")`) |
| Test | JUnit 5 + kotlin-test-junit5 + H2 in-memory |
| Coverage | Kover (HTML report, finalizes test task) |
| Connection pool | HikariCP 7.x |
| Logging | SLF4J 2.x |
| CI/CD | GitHub Actions → GitHub Packages (Maven) |
| Versioning | SemVer via git tags (`v2.0.3` → version `2.0.3`) |

## Module Map

```
kotliquery/
├── AGENTS.md                          ← you are here
├── build.gradle.kts                   ← single-module Gradle build
├── .github/
│   ├── copilot-instructions.md        ← GitHub Copilot context
│   ├── workflows/
│   │   ├── build-publish.yaml         ← tag-triggered build + publish
│   │   ├── build-pr.yaml              ← PR build + test
│   │   └── codeql-scan.yaml           ← security scanning
│   └── dependabot.yaml                ← dependency updates
├── src/main/kotlin/kotliquery/        ← library source (see AGENTS.md there)
│   ├── action/                        ← query action types (see AGENTS.md there)
│   ├── Session.kt                     ← **central class** — all query execution
│   ├── Query.kt                       ← SQL statement + params
│   ├── Row.kt                         ← ResultSet wrapper with typed accessors
│   ├── Connection.kt                  ← java.sql.Connection wrapper
│   ├── Parameter.kt                   ← typed JDBC parameter binding
│   ├── SqlValued.kt                   ← interface for custom SQL representation
│   ├── HikariCP.kt                    ← connection pool management
│   ├── LoanPattern.kt                 ← auto-close resource pattern
│   ├── TransactionalSession.kt        ← Session subclass for transactions
│   └── package.kt                     ← top-level factory functions
├── src/test/kotlin/kotliquery/        ← tests (see AGENTS.md there)
└── dokumentasjon/                     ← Norwegian docs (vedlikehold, release)
```

## Data Flow

```
User Code
  │
  ├─ queryOf("sql", params...)          → Query (immutable, reusable)
  │
  ├─ query.map { row -> ... }           → ResultQueryActionBuilder
  │   ├─ .asList                        → ListResultQueryAction
  │   └─ .asSingle                      → NullableResultQueryAction
  │
  ├─ query.asUpdate                     → UpdateQueryAction
  ├─ query.asExecute                    → ExecuteQueryAction
  ├─ query.asUpdateAndReturnGeneratedKey → UpdateAndReturnGeneratedKeyQueryAction
  │
  └─ session.run(action)                → Session dispatches via overloaded run()
      │
      ├─ Session.createPreparedStatement(query)
      │   ├─ Query.cleanStatement       (named params → ? placeholders)
      │   ├─ Session.populateParams()   (bind params to PreparedStatement)
      │   └─ Session.unwrap()           (value classes + SqlValued → raw values)
      │
      └─ JDBC execute → Row(ResultSet) → extractor → result
```

## Language Rules

- **Code, KDoc, comments, commits, PRs**: English
- **README, dokumentasjon/**: Norwegian (Bokmål)

## Build Commands

```bash
./gradlew build         # full: format + compile + test + coverage
./gradlew test          # tests only (H2 in-memory, no external DB needed)
./gradlew ktlintFormat  # format
./gradlew ktlintCheck   # lint check
```

## Release Process

Tag-driven. No version hardcoded anywhere.

```bash
git tag v2.0.3 && git push origin v2.0.3
# → CI builds, tests, publishes to GitHub Packages, creates GitHub Release
```

## Pre-commit Hook

Build task copies `.scripts/pre-commit` → `.git/hooks/pre-commit`. Runs `ktlintCheck` before commits.

## Key Gotchas for Agents

1. **ktlint runs automatically** — don't manually format to ktlint rules; `./gradlew build` handles it
2. **Named params regex**: `(?<!:):(?!:)[a-zA-Z]\w+` — avoids `::` (PostgreSQL cast) and timestamp colons
3. **Value class unwrapping** happens in `Session.unwrap()` via `kotlin-reflect` — transparent to users
4. **`SqlValued<T>` interface** allows enums/classes to define their DB representation
5. **`strict` mode** on Session/sessionOf — makes `asSingle` throw on multiple rows instead of silently taking first
6. **Transaction is always block-scoped** — no manual begin/commit API by design
7. **`TransactionalSession`** inherits `Session` — same API, just used inside `transaction { tx -> }`
8. **`returnGeneratedKeys`** defaults to `true` in Session constructor but `false` in `sessionOf()` factory
