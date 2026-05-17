# Repository

KotliQuery inkluderer to valgfrie tilnærminger for å jobbe med repositories: et annotasjonsbasert grensesnitt (`@Query`) og et Spring Data-inspirert `CrudRepository`. Begge deler er tilleggsfunksjonalitet som bygger på det grunnleggende `queryOf()`-API-et, og gir en mer deklarativ måte å organisere datatilgang på.

---

## @Query-annotert repository

Denne tilnærmingen lar deg definere et grensesnitt (interface) hvor SQL-spørringene skrives som annotasjoner direkte på metodene. KotliQuery genererer implementasjonen for deg ved kjøretid.

### Definere et repository

Bruk `@Query`-annoteringen fra pakken `kotliquery.repository` for å knytte SQL til metodene:

```kotlin
import kotliquery.repository.*

interface PersonRepository {
    @Query("SELECT * FROM person WHERE lastname = :lastname")
    fun findByLastname(lastname: String): List<Person>

    @Query("SELECT * FROM person WHERE id = :id")
    fun findById(id: Int): Person?

    @Query("INSERT INTO person (name) VALUES (:name)")
    fun insert(name: String): Int

    @Query("UPDATE person SET name = :name WHERE id = :id")
    fun update(id: Int, name: String): Boolean
}
```

### Opprette og bruke repositoriet

Du oppretter en instans av repositoriet ved å bruke `session.asRepository<T>()`. Siden grensesnittet ofte returnerer domeneobjekter, må du sende med en mapper-funksjon som forklarer hvordan en `Row` skal gjøres om til objektet ditt.

```kotlin
val repo = session.asRepository<PersonRepository> { row ->
    Person(
        id = row.int("id"), 
        name = row.string("name"),
        lastname = row.string("lastname")
    )
}

val people = repo.findByLastname("Smith")
val count = repo.insert("John")
```

### Returtyper og utførelsesmodus

Returtypen på metoden bestemmer hvordan KotliQuery utfører spørringen:

| Returtype | Utførelsesmodus | Beskrivelse |
|---|---|---|
| `List<T>` | `asList` | Returnerer alle rader som treffer spørringen. |
| `T?` | `asSingle` | Returnerer én rad, eller null hvis ingen treff. |
| `Int` | `asUpdate` | Returnerer antall rader som ble påvirket (INSERT/UPDATE/DELETE). |
| `Long?` | `asUpdateAndReturnGeneratedKey` | Returnerer den genererte nøkkelen (f.eks. auto-increment ID). |
| `Boolean` | `asUpdate` | Returnerer `true` hvis minst én rad ble påvirket. |
| `Unit` | `asExecute` | Utfører spørringen uten å returnere noe. |

### Standardmetoder (Default methods)

Siden dette er vanlige Kotlin-grensesnitt, kan du legge til logikk ved hjelp av `default`-metoder (eller metoder med kropp i Kotlin):

```kotlin
interface PersonRepository {
    @Query("SELECT * FROM person WHERE id = :id")
    fun findById(id: Int): Person?

    fun findOrThrow(id: Int): Person = findById(id) 
        ?: throw NoSuchElementException("Fant ikke person med id $id")
}
```

---

## CrudRepository

For standard CRUD-operasjoner (Create, Read, Update, Delete) tilbyr KotliQuery et ferdigbygd grensesnitt som ligner på Spring Data JDBC. Dette krever at du annoterer entitetene dine slik at biblioteket vet hvilken tabell og hvilke kolonner som skal brukes.

### Entitet-annotasjoner

Bruk annotasjoner fra `kotliquery.crud`-pakken:

- `@Table("navn")`: Definerer tabellnavnet (påkrevd).
- `@Id`: Markerer primærnøkkelen (påkrevd).
- `@Column("navn")`: Valgfritt hvis kolonnenavnet i DB er annerledes enn feltnavnet i Kotlin.
- `@GeneratedValue`: Brukes hvis databasen genererer ID-en (f.eks. `serial` eller `auto_increment`).

### Eksempel med generert ID

```kotlin
import kotliquery.crud.*
import kotliquery.repository.Query // For egne spørringer på samme repo

@Table("members")
data class Member(
    @Id @GeneratedValue val id: Long = 0,
    @Column("full_name") val name: String,
    val age: Int,
)

interface MemberRepository : CrudRepository<Member, Long> {
    @Query("SELECT * FROM members WHERE full_name = :name")
    fun findByName(name: String): List<Member>
}

val repo = session.asCrudRepository<MemberRepository>()

// INSERT: Siden id er 0 og markert med @GeneratedValue, gjøres en INSERT.
// Den returnerte instansen har fått tildelt den genererte ID-en fra databasen.
val saved = repo.save(Member(name = "Alice", age = 30))

// UPDATE: Siden instansen nå har en ID som ikke er 0, gjøres en UPDATE.
val updated = repo.save(saved.copy(age = 31))

val all = repo.findAll()
val alice = repo.findByName("Alice")
```

### Eksempel uten generert ID

Hvis ID-en settes manuelt (f.eks. en naturlig nøkkel som en kode eller UUID), utelater du `@GeneratedValue`. `save()`-metoden vil da sjekke om raden finnes fra før før den bestemmer seg for INSERT eller UPDATE.

```kotlin
@Table("tags")
data class Tag(
    @Id val code: String,
    val label: String,
)

interface TagRepository : CrudRepository<Tag, String>

val repo = session.asCrudRepository<TagRepository>()
repo.save(Tag("KOTLIN", "Programmeringsspråk"))
```

### Tilgjengelige CRUD-metoder

Når du arver fra `CrudRepository<T, ID>`, får du tilgang til følgende metoder:

| Metode | Beskrivelse |
|---|---|
| `save(entity)` | Lagrer eller oppdaterer en entitet. |
| `saveAll(entities)` | Lagrer eller oppdaterer flere entiteter. |
| `findById(id)` | Henter én entitet basert på ID. |
| `findAll()` | Henter alle entiteter i tabellen. |
| `findAllById(ids)` | Henter alle entiteter som matcher de gitte ID-ene. |
| `existsById(id)` | Sjekker om en entitet med gitt ID finnes. |
| `count()` | Teller antall rader i tabellen. |
| `deleteById(id)` | Sletter raden med gitt ID. |
| `delete(entity)` | Sletter den gitte entiteten. |
| `deleteAllById(ids)` | Sletter alle rader som matcher de gitte ID-ene. |
| `deleteAll()` | Sletter alle rader i tabellen. |

### Overstyre mapping

`asCrudRepository` prøver automatisk å mappe kolonner til feltene i data-klassen din. Hvis du har spesielle behov for mapping, kan du overstyre dette ved å sende med en egen mapper:

```kotlin
val repo = session.asCrudRepository<MemberRepository> { row ->
    Member(
        id = row.long("id"),
        name = row.string("full_name"),
        age = row.int("age")
    )
}
```

---

## Viktig å vite

- **Pakker**: Husk at `@Query` finnes i `kotliquery.repository`, mens `CrudRepository` og entitets-annotasjoner finnes i `kotliquery.crud`.
- **Parametere**: Begge metodene støtter navngitte parametere (`:navn`) i SQL-strengene. Variabelnavnene i funksjonen må matche navnet i SQL-en.
- **Ytelse**: Repositories bruker Java Dynamic Proxies internt. For de aller fleste applikasjoner er dette mer enn raskt nok, men for ekstremt ytelseskritiske løkker kan rå `queryOf()` være marginalt raskere.
- **Transaksjoner**: Repositories fungerer utmerket inne i en `session.transaction { tx -> ... }` blokk. Bare bruk `tx.asRepository<T>()` eller `tx.asCrudRepository<T>()`.
- **Støttede typer**: Ved automatisk mapping i `CrudRepository` støttes alle standard JDBC-typer som KotliQuery ellers håndterer (inkludert ZonedDateTime, Instant, osv.).
