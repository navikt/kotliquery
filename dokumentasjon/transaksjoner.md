# Transaksjoner og sesjoner

## Oversikt

KotliQuery tilbyr flere måter å jobbe med databasesesjoner og transaksjoner på. Denne guiden dekker de ulike tilnærmingene — fra enklest til mest avansert.

## Sesjoner

### withSession (anbefalt)

Den enkleste måten å jobbe med en sesjon er `withSession`-utvidelsen på `DataSource`. Sesjonen opprettes, brukes, og lukkes automatisk:

```kotlin
val dataSource = HikariCP.dataSource()

val members = dataSource.withSession {
    run(queryOf("select id, name from members").map { row ->
        Member(row.int("id"), row.stringOrNull("name"))
    }.asList)
}
```

### sessionOf + using (eksisterende API)

Den tradisjonelle måten fungerer fortsatt og er fullt bakoverkompatibel:

```kotlin
using(sessionOf(dataSource)) { session ->
    session.run(queryOf("select id from members").map { row -> row.int("id") }.asList)
}
```

---

## Transaksjoner

### DataSource.transaction (anbefalt)

Den enkleste måten å bruke transaksjoner. Kombinerer opprettelse av sesjon og transaksjon i ett kall:

```kotlin
dataSource.transaction {
    run(queryOf("insert into members (name) values (?)", "Alice").asUpdate)
    run(queryOf("insert into members (name) values (?)", "Bob").asUpdate)
}
```

- Bekreftes automatisk ved suksess
- Rulles tilbake automatisk ved unntak
- Sesjonen lukkes automatisk
- `this` inne i blokken er en `TransactionalSession`

### Session.transaction (eksisterende API)

Hvis du allerede har en sesjon, kan du starte en transaksjon direkte:

```kotlin
using(sessionOf(dataSource)) { session ->
    session.transaction { tx ->
        tx.run(queryOf("insert into members (name) values (?)", "Alice").asUpdate)
    }
}
```

---

## Dele transaksjoner mellom klasser

En vanlig utfordring er at operasjoner i forskjellige klasser (f.eks. repositories) må skje i samme transaksjon. KotliQuery løser dette uten ekstra abstraksjoner — `TransactionalSession` arver fra `Session`, så repositories kan akseptere `Session` som parameter.

### Eksempel: Repositories med delt transaksjon

```kotlin
class MemberRepository {
    fun insert(session: Session, name: String): Int =
        session.run(
            queryOf("insert into members (name, created_at) values (?, ?)", name, Date()).asUpdate
        )

    fun findByName(session: Session, name: String): Member? =
        session.run(
            queryOf("select id, name, created_at from members where name = ?", name)
                .map { row -> Member(row.int("id"), row.stringOrNull("name"), row.zonedDateTime("created_at")) }
                .asSingle
        )
}

class AuditRepository {
    fun log(session: Session, message: String): Int =
        session.run(
            queryOf("insert into audit_log (message, created_at) values (?, ?)", message, Date()).asUpdate
        )
}
```

### Bruk i en transaksjon

```kotlin
val memberRepo = MemberRepository()
val auditRepo = AuditRepository()

dataSource.transaction {
    memberRepo.insert(this, "Alice")
    auditRepo.log(this, "La til medlem: Alice")
}
```

Begge operasjonene skjer i samme transaksjon. Hvis noe feiler, rulles alt tilbake:

```kotlin
try {
    dataSource.transaction {
        memberRepo.insert(this, "Alice")
        auditRepo.log(this, "La til medlem: Alice")
        throw RuntimeException("Noe gikk galt")
    }
} catch (e: RuntimeException) {
    // Både member-innsettingen og audit-loggen er rullet tilbake
}
```

### Bruk utenfor transaksjon

De samme repository-klassene kan også brukes uten transaksjon:

```kotlin
dataSource.withSession {
    val member = memberRepo.findByName(this, "Alice")
}
```

---

## Sammenligning: Før og etter

### Enkel spørring

```kotlin
// Før:
using(sessionOf(dataSource)) { session ->
    session.run(queryOf("select count(*) from members").map { it.int(1) }.asSingle)
}

// Etter:
dataSource.withSession {
    run(queryOf("select count(*) from members").map { it.int(1) }.asSingle)
}
```

### Transaksjon

```kotlin
// Før (3 nivåer med nøsting):
using(sessionOf(dataSource)) { session ->
    session.transaction { tx ->
        tx.run(queryOf("insert into members (name) values (?)", "Alice").asUpdate)
    }
}

// Etter (1 nivå):
dataSource.transaction {
    run(queryOf("insert into members (name) values (?)", "Alice").asUpdate)
}
```

### Delt transaksjon mellom klasser

```kotlin
// Før:
using(sessionOf(dataSource)) { session ->
    session.transaction { tx ->
        memberRepo.insert(tx, "Alice")
        auditRepo.log(tx, "La til Alice")
    }
}

// Etter:
dataSource.transaction {
    memberRepo.insert(this, "Alice")
    auditRepo.log(this, "La til Alice")
}
```

---

## API-referanse

| Funksjon | Beskrivelse |
|---|---|
| `DataSource.withSession { }` | Oppretter sesjon, kjører blokk, lukker sesjon |
| `DataSource.transaction { }` | Oppretter sesjon + transaksjon, bekrefter/ruller tilbake, lukker |
| `Session.transaction { tx -> }` | Starter transaksjon på eksisterende sesjon |

Alle funksjonene støtter valgfrie parametere: `returnGeneratedKey`, `strict`, og `queryTimeout`.
