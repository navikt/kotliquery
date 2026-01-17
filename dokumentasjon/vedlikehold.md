# Vedlikeholdsguide

Hvordan bidra til KotliQuery.

## Git-strategi

### Branch-naming

Bruk beskrivende prefix:

```bash
release/2.0.5                # Release branches
feature/add-batch-insert     # Nye features
fix/connection-leak          # Bugfixes
bugfix/null-pointer          # Bugfixes
docs/update-readme           # Dokumentasjon
chore/upgrade-kotlin         # Vedlikehold
refactor/simplify-query      # Refaktorering
cleanup/remove-deprecated    # Rydding
deps/update-hikari           # Dependencies
dependabot/...               # Dependabot PRs
```

### Release notes

Release notes viser **kun PR-titler** fra PRs merged til release branchen:

**Hvordan det fungerer:**
- Squash merge samler alle commits i én PR
- Kun PR-tittelen vises i release notes
- Individuelle commits vises ikke (ingen filtrering nødvendig)
- Bump commit PR vises heller ikke siden den merges direkte til release

**Eksempel:**

```markdown
## Changelog

* Add batch insert support (#42) @username
* Fix connection leak in HikariCP (#43) @username
* Upgrade Kotlin to 2.0.5 (#44) @username

**Full Changelog**: https://github.com/navikt/kotliquery/commits/v2.0.5
```

**Viktig:** PR-tittelen er det som vises, så bruk beskrivende titler!

---

## Lage en release

### 1. Opprett release branch fra main

```bash
git checkout main
git pull origin main
git checkout -b release/2.0.5
```

**Viktig:** Branch-navn MÅ matche versjonsnummer (format: `x.x.x` - kun tall)!

### 2. Bump versjon

Rediger `build.gradle.kts`:

```kotlin
version = "2.0.5"  // Samme som branch-navn! Kun tall: x.x.x
```

**Validering:** Versjonsformatet valideres automatisk ved merge til main.
- ✅ `2.0.5` - godkjent
- ❌ `2.0.5-SNAPSHOT` - feiler
- ❌ `v2.0.5` - feiler
- ❌ `2.0` - feiler (må ha tre tall)

```bash
git add build.gradle.kts
git commit -m "bump kotliquery to 2.0.5"
git push origin release/2.0.5
```

### 3. Merge features til release branch

For hver feature/fix/chore som skal med i releasen:

```bash
# Branch ut FRA main
git checkout main
git pull origin main
git checkout -b feature/my-feature

# ELLER bugfix/chore/etc
git checkout -b bugfix/fix-connection-leak
git checkout -b chore/upgrade-kotlin

# Gjør endringer
git add .
git commit -m "Add batch insert support"  # ✅ Beskrivende!
git push origin feature/my-feature
```

**Opprett PR:** `feature/my-feature` → `release/2.0.5`

**Merge:** Velg **"Squash and merge"** ✅

**Hvorfor squash?** 
- En ren commit per feature
- PR-tittel blir commit-melding
- Overskuelig historikk
- Vises i release notes

### 4. Merge release til main

**Opprett PR:** `release/2.0.5` → `main`

**Merge:** Velg **"Squash and merge"** ✅

**Hvorfor squash?**
- Konsistent strategi hele veien
- Ryddig historikk i main
- Release notes viser fortsatt alle PRs som ble merged til release

### 5. Automatisk release

Når release-PR merges til main:
1. Versjon valideres (må være `x.x.x` format)
2. Tag `v2.0.5` opprettes automatisk
3. Release notes genereres med alle PRs merged til release branch
4. Publiseres til GitHub Packages

---

## Commit-meldinger og PR-titler

**Viktig:** Kun **PR-titler** vises i release notes, ikke individuelle commits!

### ✅ Gode PR-titler:

```
Add batch insert support
Fix connection leak in HikariCP
Update README with new examples
Upgrade Kotlin to 2.0.5
```

### ❌ Dårlige PR-titler:

```
fix                    # Hva ble fikset?
wip                    # Ikke beskrivende
update                 # Hva ble oppdatert?
PR from feature branch # 🤦
```

**Tips for commits:**
- Du kan bruke flere commits i en PR (squashes til én)
- Commit-meldinger vises ikke i release notes
- Fokuser på god PR-tittel!

**Tips:** PR-tittelen leses i release notes om 6 måneder - gjør den beskrivende!

---

## Merge-strategi oppsummering

| Fra → Til | Merge type | Hvorfor |
|-----------|------------|---------|
| `feature/*`, `bugfix/*`, `chore/*` → `release/*` | **Squash merge** | Ryddig historikk, én PR per feature |
| `release/*` → `main` | **Squash merge** | Konsistent hele veien, ryddig main-historikk |

**Viktig:** Release notes genereres fra **PR-titler** merged til release branch, ikke fra commit-historikk.

**Hvorfor vises ikke bump commit?** 
- Bump commit pushes direkte til release branch (ikke via PR)
- Kun PRs vises i release notes
---

## Versjonering

Følg [Semantic Versioning](https://semver.org/):

```
MAJOR.MINOR.PATCH

2.0.5
│ │ └─ Patch: Bugfixes, ingen breaking changes
│ └─── Minor: Nye features, bakoverkompatibel
└───── Major: Breaking changes
```

**Eksempler:**
- `2.0.5` → `2.0.6` - Bugfix
- `2.0.6` → `2.1.0` - Ny feature
- `2.1.0` → `3.0.0` - Breaking change

---

## Testing

### Lokalt

```bash
./gradlew clean build
./gradlew test
```

### I PR

Workflows kjører automatisk:
- `build-pr.yaml` - Bygger og tester

Sjekk at alt er grønt før merge!

---

## Vanlige problemer

### Problem: Release notes er tomme eller mangler PRs

**Årsak:** 
- PRs ble ikke merged til release branch
- PRs ble merged etter at release ble tagget

**Løsning:** 
- Sørg for at alle features/bugfixes squash merges til release branch FØR release merges til main
- Cherry-pick til neste release eller lag hotfix


### Problem: Feil versjon i tag

**Årsak:** Versjon i `build.gradle.kts` matcher ikke branch-navn eller har feil format

**Løsning:**
```bash
# Slett feil tag
git tag -d v2.0.5
git push origin :refs/tags/v2.0.5

# Fix versjon (må være x.x.x format) og prøv igjen
```

### Problem: Versjon-validering feiler

**Årsak:** Versjonen i `build.gradle.kts` følger ikke `x.x.x` formatet

**Løsning:**
- ✅ Bruk: `version = "2.0.5"`
- ❌ Ikke: `version = "2.0.5-SNAPSHOT"`
- ❌ Ikke: `version = "v2.0.5"`

---

## Dokumentasjonsendringer

Dokumentasjon kan merges direkte til `main`:

```bash
git checkout main
git checkout -b docs/update-readme

# Gjør endringer
git add .
git commit -m "Update README with new examples"
git push origin docs/update-readme
```

**Opprett PR:** `docs/*` → `main` (direkte!)

**Merge:** Squash merge (samme som alle andre PRs)

**Resultat:** Ingen release trigges, kun dokumentasjon oppdateres.
