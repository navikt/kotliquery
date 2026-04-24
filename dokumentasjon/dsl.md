# Type-safe SQL DSL

KotliQuery inkluderer en valgfri type-safe DSL som lar deg bygge SQL-spørringer med Kotlin-funksjoner i stedet for rå SQL-strenger. DSL-en bruker infix-funksjoner for å gi en lesbar, funksjonell syntaks som ligner på Spring Data JDBC, men med idiomatisk Kotlin-stil.

> **Merk:** DSL-en er et tillegg til den eksisterende `queryOf()`-API-en. Du kan blande begge fritt i samme prosjekt.

## Tabelldefinisjoner

Definer tabeller som Kotlin-objekter som arver fra `Table`:

```kotlin
import kotliquery.dsl.*

object Members : Table("members") {
    val id = integer("id")
    val name = varchar("name")
    val createdAt = timestamp("created_at")
}
```

### Tilgjengelige kolonnetyper

| Funksjon | Kotlin-type |
|---|---|
| `integer` | `Int` |
| `long` | `Long` |
| `short` | `Short` |
| `float` | `Float` |
| `double` | `Double` |
| `boolean` | `Boolean` |
| `varchar` | `String` |
| `text` | `String` |
| `bigDecimal` | `BigDecimal` |
| `timestamp` | `ZonedDateTime` |
| `instant` | `Instant` |
| `localDateTime` | `LocalDateTime` |
| `localDate` | `LocalDate` |
| `localTime` | `LocalTime` |
| `offsetDateTime` | `OffsetDateTime` |
| `uuid` | `UUID` |
| `byteArray` | `ByteArray` |
| `column<T>` | Generisk type |

---

## SELECT-spørringer

### Hent alle kolonner

```kotlin
select(All) from Members
```

### Hent spesifikke kolonner

```kotlin
select(Members.id, Members.name) from Members
```

### Med betingelser (WHERE)

```kotlin
select(All) from Members where (Members.name eq "Alice")
```

### Sortering og begrensning

```kotlin
select(All) from Members orderBy Members.name.asc() limit 10
select(All) from Members orderBy Members.createdAt.desc()
```

### Sammensatte betingelser

```kotlin
select(All) from Members where (
    (Members.name eq "Alice") and (Members.id gt 5)
)

select(All) from Members where (
    (Members.name eq "Alice") or (Members.name eq "Bob")
)
```

---

## INSERT-spørringer

### Sett inn én rad

```kotlin
insertInto(Members).values(
    Members.name to "Alice",
    Members.createdAt to Date()
)
```

### Infix-syntaks for enkeltverdi

```kotlin
insertInto(Members) values (Members.name to "Bob")
```

---

## UPDATE-spørringer

```kotlin
update(Members) set (Members.name to "Bob") where (Members.id eq 1)
```

### Flere kolonner

```kotlin
update(Members).set(
    Members.name to "Charlie",
    Members.createdAt to Date()
) where (Members.id eq 1)
```

---

## DELETE-spørringer

```kotlin
deleteFrom(Members) where (Members.id eq 1)
```

---

## Betingelsesoperatorer

| Operator | Beskrivelse | Eksempel |
|---|---|---|
| `eq` | Lik (`=`) | `Members.name eq "Alice"` |
| `neq` | Ikke lik (`!=`) | `Members.name neq "Bob"` |
| `gt` | Større enn (`>`) | `Members.id gt 5` |
| `lt` | Mindre enn (`<`) | `Members.id lt 10` |
| `gte` | Større enn eller lik (`>=`) | `Members.id gte 1` |
| `lte` | Mindre enn eller lik (`<=`) | `Members.id lte 100` |
| `like` | Mønstertreff (`LIKE`) | `Members.name like "A%"` |
| `within` | IN-klausul | `Members.id within listOf(1, 2, 3)` |
| `between` | Mellom to verdier | `Members.id between 1..10` |
| `isNull()` | Er NULL | `Members.name.isNull()` |
| `isNotNull()` | Er ikke NULL | `Members.name.isNotNull()` |
| `and` | Logisk OG | `(a eq 1) and (b eq 2)` |
| `or` | Logisk ELLER | `(a eq 1) or (b eq 2)` |
| `not()` | Logisk IKKE | `not(Members.name eq "X")` |

---

## Kjøring med KotliQuery-sesjon

DSL-spørringer bygger `Query`-objekter som brukes med den vanlige KotliQuery-sesjonen:

### SELECT med rad-mapping

```kotlin
val toMember: (Row) -> Member = { row ->
    Member(
        row.int("id"),
        row.stringOrNull("name"),
        row.zonedDateTime("created_at")
    )
}

// Hent alle som liste
val members: List<Member> = session.run(
    (select(All) from Members).map(toMember).asList
)

// Hent én rad
val alice: Member? = session.run(
    (select(All) from Members where (Members.name eq "Alice"))
        .map(toMember).asSingle
)
```

### UPDATE / DELETE

```kotlin
// Oppdater
session.run(
    (update(Members) set (Members.name to "Bob") where (Members.id eq 1)).asUpdate
)

// Slett
session.run(
    (deleteFrom(Members) where (Members.id eq 1)).asUpdate
)
```

### INSERT

```kotlin
session.run(
    insertInto(Members).values(Members.name to "Alice", Members.createdAt to Date()).asUpdate
)
```

### I transaksjoner

DSL-spørringer fungerer likt i transaksjoner:

```kotlin
session.transaction { tx ->
    tx.run(insertInto(Members).values(Members.name to "Alice", Members.createdAt to Date()).asUpdate)
    tx.run((update(Members) set (Members.name to "Bob") where (Members.name eq "Alice")).asUpdate)
}
```

---

## Konvertering til Query-objekt

Alle DSL-spørringer kan konverteres til et `Query`-objekt med `toQuery()`:

```kotlin
val query: Query = (select(All) from Members where (Members.name eq "Alice")).toQuery()
```

Dette er nyttig hvis du trenger mer kontroll, eller vil bruke spørringen med andre KotliQuery-funksjoner.

---

## Viktig å vite

- **Type-sikkerhet**: Kolonneoperatorer er typet — `Members.id gt "tekst"` gir kompileringsfeil
- **Parametriserte spørringer**: Alle verdier bindes som `?`-parametere, aldri som rå strenger — trygt mot SQL-injeksjon
- **Kompatibilitet**: DSL-en bygger på den eksisterende `queryOf()`-mekanismen og fungerer med alle KotliQuery-funksjoner
- **Valgfritt**: DSL-en er helt valgfri — du kan fortsette å bruke `queryOf()` med rå SQL
