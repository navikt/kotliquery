# Transaksjoner og sesjoner

## Oversikt

KotliQuery tilbyr to hovedmønstre for å jobbe med databasesesjoner og transaksjoner:

1. **Transparent mønster (anbefalt)**: Repositories tar inn en `DataSource` og deltar automatisk i aktive transaksjoner via thread-local binding. Dette ligner på hvordan Spring håndterer transaksjoner.
2. **Eksplisitt mønster (bakoverkompatibelt)**: Sesjoner eller transaksjoner sendes manuelt som parametere til funksjoner.

## Sesjoner

### withSession (anbefalt)

Den enkleste måten å jobbe med en sesjon på er `withSession`-utvidelsen på `DataSource`. Sesjonen opprettes, brukes, og lukkes automatisk:

```kotlin
val dataSource = HikariCP.dataSource()

val members = dataSource.withSession {
    run(queryOf("select id, name from members").map { row ->
        Member(row.int("id"), row.stringOrNull("name"))
    }.asList)
}
```

Hvis `withSession` kalles inne i en aktiv transaksjon på samme `DataSource`, vil den automatisk gjenbruke den eksisterende transaksjonstilkoblingen.

### sessionOf + using (bakoverkompatibelt API)

Den tradisjonelle måten fungerer fortsatt og er fullt bakoverkompatibel:

```kotlin
using(sessionOf(dataSource)) { session ->
    session.run(queryOf("select id from members").map { row -> row.int("id") }.asList)
}
```

---

## Transaksjoner

### Transparent transaksjon (anbefalt)

Med dette mønsteret trenger ikke koden din å vite om den kjører i en transaksjon eller ikke. Repositories defineres med en `DataSource`, og bruker `withSession` internt.

```kotlin
class MemberRepository(private val dataSource: DataSource) {
    fun insert(name: String): Int =
        dataSource.withSession {
            run(queryOf("insert into members (name, created_at) values (?, ?)", name, Date()).asUpdate)
        }
}

class AuditRepository(private val dataSource: DataSource) {
    fun log(message: String): Int =
        dataSource.withSession {
            run(queryOf("insert into audit_log (message) values (?)", message).asUpdate)
        }
}

// Transparent transaksjon — begge operasjonene skjer i samme transaksjon:
dataSource.transaction {
    memberRepo.insert("Alice")
    auditRepo.log("la til Alice")
}
```

Når `dataSource.transaction` starter, blir tilkoblingen bundet til den gjeldende tråden. Alle påfølgende kall til `withSession` på **samme DataSource-instans** vil automatisk bruke denne tilkoblingen.

### Transaksjonsinnstillinger

Du kan tilpasse transaksjonen med flere parametere:

```kotlin
dataSource.transaction(
    readOnly = true,
    isolation = TransactionIsolation.READ_COMMITTED,
    queryTimeout = 30 // sekunder
) {
    // ...
}
```

Tilgjengelige isolasjonsnivåer i `TransactionIsolation`: `READ_UNCOMMITTED`, `READ_COMMITTED`, `REPEATABLE_READ`, `SERIALIZABLE`.

### Tilbakerullingsregler

Som standard rulles transaksjonen tilbake ved alle typer unntak (`Throwable`). Du kan spesifisere unntak som *ikke* skal føre til rollback ved å bruke `noRollbackFor`:

```kotlin
dataSource.transaction(noRollbackFor = setOf(MyBusinessException::class)) {
    run(queryOf("insert into members (name) values (?)", "Alice").asUpdate)
    throw MyBusinessException("Dette ruller ikke tilbake")
}
```

I dette eksempelet vil "Alice" bli lagret i databasen selv om unntaket kastes.

### Nestede transaksjoner

Hvis du kaller `dataSource.transaction` inne i en allerede pågående transaksjon på samme `DataSource`, vil den nestede blokken automatisk delta i den eksisterende transaksjonen. Dette tilsvarer `PROPAGATION_REQUIRED` i Spring.

---

## Eksplisitt sesjonshåndtering (bakoverkompatibelt)

Hvis du foretrekker å sende sesjonen manuelt, eller jobber i en kodebase som allerede gjør dette, støtter KotliQuery dette fullt ut.

### Eksempel: Repositories med eksplisitt Session

```kotlin
class MemberRepository {
    fun insert(session: Session, name: String): Int =
        session.run(queryOf("insert into members (name) values (?)", name).asUpdate)
}

// Bruk:
dataSource.transaction {
    memberRepo.insert(this, "Alice")
}
```

---

## Sammenligning: Tre mønstre

Her er en sammenligning av hvordan man utfører en transaksjon på tvers av to repositories:

### 1. Transparent (Ny anbefaling)
Ingen sesjonsparametere. Repositories eier sin egen `DataSource`.

```kotlin
dataSource.transaction {
    memberRepo.insert("Alice")
    auditRepo.log("la til Alice")
}
```

### 2. Eksplisitt (Delt Session)
Sesjonen sendes som parameter. Gir full kontroll, men mer støy i API-et.

```kotlin
dataSource.transaction {
    memberRepo.insert(this, "Alice")
    auditRepo.log(this, "la til Alice")
}
```

### 3. Manuelt (Eldre API)
Mest støy, men viser hva som skjer under panseret.

```kotlin
using(sessionOf(dataSource)) { session ->
    session.transaction { tx ->
        memberRepo.insert(tx, "Alice")
        auditRepo.log(tx, "la til Alice")
    }
}
```

---

## API-referanse

| Funksjon | Beskrivelse | Parametere |
|---|---|---|
| `DataSource.withSession { }` | Oppretter eller gjenbruker sesjon | `returnGeneratedKey`, `strict`, `queryTimeout` |
| `DataSource.transaction { }` | Starter en transparent transaksjon | `readOnly`, `isolation`, `queryTimeout`, `noRollbackFor`, `strict` |
| `Session.transaction { tx -> }` | Starter transaksjon på eksisterende sesjon | `isolation`, `noRollbackFor` |
| `sessionOf(DataSource, ...)` | Oppretter en ny sesjon manuelt | `returnGeneratedKey`, `strict`, `queryTimeout` |

**Viktig:** Den transparente transaksjonshåndteringen er basert på objekt-identitet (`IdentityHashMap`). Du må bruke den **samme DataSource-instansen** gjennom hele transaksjonskjeden for at bindingen skal fungere.

