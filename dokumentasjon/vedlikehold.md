# Vedlikehold av KotliQuery

## 🚀 Hvordan lage en release

### Enkel prosess:

```bash
# 1. Sørg for at main er oppdatert
git checkout main
git pull origin main

# 2. Lag en Git tag (versjonsnummer styrer alt)
git tag v2.0.3

# 3. Push taggen
git push origin v2.0.3

# 4. Ferdig! ✅
```

**Hva skjer automatisk:**
- ✅ Koden bygges og testes
- ✅ Pakke publiseres til GitHub Packages som `kotliquery-2.0.3.jar`
- ✅ GitHub Release opprettes med tittel `v2.0.3`
- ✅ Release notes genereres automatisk fra commits

**Viktig:** Versjonsnummeret hentes fra taggen (ikke hardkodet noe sted).

---

## 🗑️ Slette release hvis noe går galt

### Scenario 1: Release feilet under bygging

```bash
# Slett taggen lokalt
git tag -d v2.0.3

# Slett taggen remote
git push origin :refs/tags/v2.0.3

# Fiks problemet, deretter lag taggen på nytt
git tag v2.0.3
git push origin v2.0.3
```

### Scenario 2: Release ble opprettet, men er feil

```bash
# 1. Slett GitHub Release i GitHub UI:
#    Gå til: https://github.com/navikt/kotliquery/releases
#    Klikk på release → "Delete release" (øverst til høyre)

# 2. Slett taggen
git tag -d v2.0.3
git push origin :refs/tags/v2.0.3

# 3. Fiks problemet, lag taggen på nytt
git tag v2.0.3
git push origin v2.0.3
```

### Scenario 3: Pakke ble publisert med feil versjon

**Problem:** Kan ikke overskrive pakker i GitHub Packages.

**Løsning:** Lag en ny patch-versjon (f.eks. `v2.0.4`) med fiksen.

---

## 📝 Release notes - Hva vises?

### Hva genereres automatisk:

Release notes viser **alle commits** mellom forrige tag og ny tag:

```markdown
## What's Changed

* Bump kotlin from 2.3.0 to 2.3.1 (abc123)
* Fix memory leak in connection pool (def456)
* Add support for batch queries (ghi789)

**Full Changelog**: https://github.com/navikt/kotliquery/compare/v2.0.2...v2.0.3
```

### ✅ Beste praksis for commit-meldinger:

**Bra commits (tydelige):**
- ✅ `Bump kotlin from 2.3.0 to 2.3.1`
- ✅ `Fix memory leak in connection pool`
- ✅ `Add support for batch queries`
- ✅ `Remove deprecated session methods`

**Dårlige commits (utydelige):**
- ❌ `Update stuff`
- ❌ `Fix bug`
- ❌ `WIP`
- ❌ `asdfsadf`

**Hvorfor dette er viktig:**
- Commit-meldinger vises **direkte** i release notes
- Brukere ser disse når de vurderer å oppgradere
- God historikk gjør det lettere å finne endringer senere

### 💡 Tips for bedre release notes:

1. **Bruk PR-titler som commit-meldinger**
   - Squash & merge PRs med god tittel
   - PR-tittelen blir commit-meldingen

2. **Følg en konvensjon:**
   - `feat: Add new feature` (nye features)
   - `fix: Fix bug description` (bug fixes)
   - `chore: Update dependencies` (vedlikehold)
   - `docs: Update README` (dokumentasjon)

3. **Dependabot uten grouping:**
   - Hver dependency får sin egen PR
   - Hver oppdatering vises som egen linje i notes
   - Bedre oversikt over hva som ble oppdatert

---

## 🔧 Versjonering

Vi følger [Semantic Versioning](https://semver.org/):

- **Major** (`v3.0.0`) - Breaking changes (inkompatible endringer)
- **Minor** (`v2.1.0`) - Nye features (bakoverkompatible)
- **Patch** (`v2.0.1`) - Bug fixes (bakoverkompatible)

**Eksempler:**
```bash
# Bug fix
git tag v2.0.4

# Ny feature
git tag v2.1.0

# Breaking change
git tag v3.0.0
```

## 📋 Sjekkliste før release

Før du lager en ny release:

- [ ] Alle tester passerer på `main`
- [ ] Alle ønskede PRs er merget
- [ ] README er oppdatert (hvis nødvendig)
- [ ] Breaking changes er dokumentert (hvis major version)
- [ ] Lokal bygg fungerer: `./gradlew clean build`

---

## ❓ Feilsøking

### Problem: "Failed to publish - 409 Conflict"

**Årsak:** Prøver å publisere en versjon som allerede eksisterer.

**Løsning:** Lag en ny versjon (f.eks. `v2.0.4` i stedet for `v2.0.3`).

### Problem: Release notes er tomme

**Årsak:** Ingen commits mellom forrige tag og ny tag.

**Løsning:** Dette er normalt hvis det ikke har vært noen endringer. Ikke lag en release uten endringer.

### Problem: Workflow trigges ikke

**Årsak:** Tag matcher ikke pattern `v*`.

**Løsning:** Tags må starte med `v` (f.eks. `v2.0.3`, ikke `2.0.3`).

---

## 💬 Spørsmål?

Spørsmål knyttet til vedlikehold kan stilles i Slack-kanalen [#kotliquery-maintainers](https://nav-it.slack.com/archives/C0A97T61BTN).
