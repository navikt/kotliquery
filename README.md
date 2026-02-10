# KotliQuery

KotliQuery er et praktisk RDB-klientbibliotek for Kotlin-utviklere! Designet er sterkt inspirert av [ScalikeJDBC](http://scalikejdbc.org/), som er et velprøvd databasebibliotek i Scala. Prioriteringene i dette prosjektet er:

* Kort læringstid
* Ingen endringer som ødelegger bakoverkompatibilitet i nye versjoner
* Ingen unødvendig kompleksitet utover JDBC

Dette biblioteket gjør det enklere å bruke JDBC, men målet vårt er ikke å skjule alt ved JDBC.

## Takk

Dette biblioteket er en fork av av [seratch](https://github.com/seratch) sin [KotliQuery](https://github.com/seratch/kotliquery).

## Kom i gang

Du kan enkelt legge til KotliQuery i ditt prosjekt, enten du bruker Maven eller Gradle.

**Gradle**

Legg til følgende avhengighet i `build.gradle.kts` eller `build.gradle`:

```kotlin
dependencies {
    implementation("no.nav:kotliquery:2.0.0")
}
```

**Maven**

Legg til følgende i `pom.xml`:

```xml
<dependency>
    <groupId>no.nav</groupId>
    <artifactId>kotliquery</artifactId>
    <version>2.0.0</version>
</dependency>
```

> **Tips:** Husk å oppdatere versjonsnummeret hvis det kommer nyere versjoner.

### Eksempel

KotliQuery er mye enklere å bruke enn du kanskje tror. Etter å ha lest denne korte seksjonen, har du lært det viktigste.

#### Opprette databasesesjon

Det første du gjør er å opprette et `Session`-objekt, som er et tynt omslag rundt en `java.sql.Connection`-instans. Med dette objektet kan du kjøre spørringer mot en etablert databaseforbindelse.

```kotlin
import kotliquery.*

val session = sessionOf("jdbc:h2:mem:hello", "user", "pass") 
```

#### HikariCP

For applikasjoner i produksjon anbefales det å bruke et tilkoblingspool-bibliotek for bedre ytelse og ressursstyring. KotliQuery tilbyr en ferdig løsning som bruker [HikariCP](https://github.com/brettwooldridge/HikariCP), et anerkjent bibliotek for tilkoblingspooling.

```kotlin
HikariCP.default("jdbc:h2:mem:hello", "user", "pass")

sessionOf(HikariCP.dataSource()).use { session ->
   // arbeid med sesjonen
}
```

#### Kjøring av DDL

Du kan bruke en sesjon til å kjøre både DDL- og DML-setninger. Metoden `asExecute` på et spørringsobjekt setter den underliggende JDBC Statement-metoden til [`execute`](https://docs.oracle.com/javase/8/docs/api/java/sql/Statement.html#execute-java.lang.String-).

```kotlin
session.run(queryOf("""
  create table members (
    id serial not null primary key,
    name varchar(64),
    created_at timestamp not null
  )
""").asExecute) // returnerer Boolean
```

#### Oppdateringsoperasjoner

Ved å bruke `asUpdate` kan du enkelt utføre innsettings-, oppdaterings- og slettingssetninger. Denne metoden setter den underliggende JDBC Statement-metoden til [`executeUpdate`](https://docs.oracle.com/javase/8/docs/api/java/sql/Statement.html#executeUpdate-java.lang.String-). 

```kotlin
val insertQuery: String = "insert into members (name,  created_at) values (?, ?)"

session.run(queryOf(insertQuery, "Alice", Date()).asUpdate) // returnerer antall berørte rader
session.run(queryOf(insertQuery, "Bob", Date()).asUpdate)
```

#### Spørringer

Nå som du har fått opprettet en databasetabell kalt `members`, er det på tide å kjøre din første SQL-setning med dette biblioteket! For å bygge en kallbar SQL-utøver, følger koden din tre enkle trinn:

- Bruk `queryOf`-fabrikkmetoden med en spørringssetning og dens parametere for å opprette et nytt `Query`-objekt
- Bruk `#map`-metoden for å knytte en funksjon for å hente ut resultater (`(Row) -> A`) til `Query`-objektet
- Spesifiser svaretype (`asList`/`asSingle`) for resultatet

Den følgende spørringen returnerer en liste over alle medlemmers ID-er. I denne linjen blir ikke SQL-setningen utført ennå. Også, dette objektet `allIdsQuery` har ingen tilstand. Det betyr at du kan gjenbruke objektet flere ganger.

```kotlin
val allIdsQuery = queryOf("select id from members").map { row -> row.int("id") }.asList
```

Med et gyldig sesjonsobjekt kan du utføre SQL-setningen. Typen av den returnerte `ids` vil bli trygt bestemt av Kotlin-kompilatoren.

```kotlin
val allIds: List<Int> = session.run(allIdsQuery)
```

Som du ser, er ekstraktorfunksjonen svært fleksibel. Du kan definere funksjoner med hvilken som helst returtype. Alt du trenger å gjøre er å implementere en funksjon som henter ut verdier fra JDBC `ResultSet`-iteratoren og mapper dem til en enkelt forventet typeverdi. Her er et komplett eksempel:

```kotlin
data class Member(
  val id: Int,
  val name: String?,
  val createdAt: java.time.ZonedDateTime)

val toMember: (Row) -> Member = { row -> 
  Member(
    row.int("id"), 
    row.stringOrNull("name"), 
    row.zonedDateTime("created_at")
  )
}

val allMembersQuery = queryOf("select id, name, created_at from members").map(toMember).asList
val allMembers: List<Member> = session.run(allMembersQuery)
```

Hvis du er sikker på at en spørring kan returnere null eller én rad, returnerer `asSingle` en valgfri enkeltverdi som vist nedenfor:

```kotlin
val aliceQuery = queryOf("select id, name, created_at from members where name = ?", "Alice").map(toMember).asSingle
val alice: Member? = session.run(aliceQuery)
```

Teknisk sett er det også mulig å bruke `asSingle` sammen med en SQL-setning som returnerer flere rader. Med standardinnstillingen returnerer datainnhentingen bare den første raden i resultatet og hopper over resten. Med andre ord, KotliQuery ignorerer stille den ineffektiviteten og den potensielle feiloppførselen. Hvis du foretrekker å oppdage dette som en feil, kan du sende `strict`-flagget til `Session`-initialisatoren. Med `strict` satt til `true`, kaster spørringskjøringen et unntak hvis den oppdager flere rader for `asSingle`.

```kotlin
// Konstruktør for Session-objekt
val session = Session(HikariCP.dataSource(), strict = true)

// en auto-lukkende kodeblokk for sesjon
sessionOf(HikariCP.dataSource(), strict = true).use { session ->

}
```


#### Navngitte spørringsparametere

En alternativ måte å binde parametere på er å bruke navngitte parametere som starter med `:` i setningstrengen. Vær oppmerksom på at, med denne funksjonen, fortsatt bruker KotliQuery en forberedt setning internt, og spørringskjøringen din er trygg mot SQL-injeksjon. Delene av parameterne som `:name` og `:age` i følgende eksempelspørring vil ikke bare bli erstattet som strengverdier.

```kotlin
queryOf(
  """
  select id, name, created_at 
  from members 
  where (name = :name) and (age = :age)
  """, 
  mapOf("name" to "Alice", "age" to 20)
)
```

Når det gjelder ytelse, kan syntaksen for navngitte parametere være litt tregere på grunn av parsing av setningen pluss en liten økning i minnebruk. Men for de fleste bruksområder bør overhodet være neglisjerbar. Hvis du ønsker å gjøre SQL-setningene dine mer lesbare, og/eller hvis spørringen din må gjenta den samme parameteren i en spørring, bør bruken av navngitte spørringsparametere forbedre produktiviteten og vedlikeholdbarheten til spørringen din betydelig.

#### Typede parametere

Du kan spesifisere Java-typen for hver parameter på følgende måte. Ved å sende klassen `Parameter` hjelper du KotliQuery med å bestemme riktig type å binde for hver parameter i spørringene.

```kotlin
val param = Parameter(param, String::class.java)
queryOf(
  """
  select id, name
  from members 
  where ? is null or ? = name
  """,
  param,
  param
)
``` 

Som en enklere måte kan du bruke følgende hjelpemetode.

```kotlin
queryOf(
  """
  select id, name 
  from members 
  where ? is null or ? = name
  """, 
  null.param<String>(),
  null.param<String>()
)
```

Denne funksjonaliteten er spesielt nyttig i situasjoner som [de som er beskrevet her](https://www.postgresql.org/message-id/6ekbd7dm4d6su5b9i4hsf92ibv4j76n51f@4ax.com).

#### Arbeide med store datamengder

Metoden `#forEach` lar deg jobbe med hver rad med mindre minneforbruk. På denne måten trenger ikke applikasjonskoden din å laste alle resultatdataene fra spørringen i minnet på en gang. Denne funksjonen er svært nyttig når du laster et stort antall rader fra en databasetabell med en enkelt spørring.

```kotlin
session.forEach(queryOf("select id from members")) { row ->
  // arbeid med store datamengder
})
```

#### Transaksjon

Det er selvfølgelig mulig å kjøre spørringer i en transaksjon! `Session`-objektet gir en måte å starte en transaksjon på i en bestemt kodeblokk.

```kotlin
session.transaction { tx ->
  // start
  tx.run(queryOf("insert into members (name,  created_at) values (?, ?)", "Alice", Date()).asUpdate)
}
// bekreft

session.transaction { tx ->
  // start
  tx.run(queryOf("update members set name = ? where id = ?", "Chris", 1).asUpdate)
  throw RuntimeException() // rull tilbake
}
```

Siden dette biblioteket har noen bestemte meninger, er transaksjoner kun tilgjengelige med en kodeblokk. Vi støtter bevisst ikke `begin` / `commit`-metoder. Hvis du av en eller annen grunn ønsker å håndtere tilstanden til en transaksjon manuelt, kan du bruke `session.connection.commit()` / `session.connection.rollback()` for det.


## Dokumentasjon

- **[Vedlikehold](dokumentasjon/vedlikehold.md)** - Hvordan lage releases og vedlikeholde prosjektet
- **[Release Quick Start](dokumentasjon/release-quick-start.md)** - Rask guide for å lage en ny release

## Spørsmål

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på Github.
Henvendelser kan også sendes via Slack i kanalen [#kotliquery-maintainers](https://nav-it.slack.com/archives/C0A97T61BTN)
