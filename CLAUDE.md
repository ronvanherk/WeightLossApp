# WeightLossApp

## What this is

A private calorie and weight tracker in the spirit of Yazio, for **two users: Ron and Cindy**.
Not a product: no sign-up, no payments, no store listing. Started 2026-10-17.

- **Android only, native** (Kotlin + Jetpack Compose). iOS is not planned; if it ever is, move
  the data layer into a Kotlin Multiplatform module rather than rewriting.
- **Online-only is fine.** The app holds no database of its own; everything comes from the
  server. Offline logging was explicitly traded away for simplicity.
- **Food data for three countries: NL, DK, DE.** The household lives near the Danish-German
  border, so Danish and German products matter as much as Dutch ones.

## Layout

```
server/    Next.js 16 + Prisma 6 API (and later a web view), own Postgres
android/   Kotlin + Compose app talking to server/ over /api/v1
.github/   CI: test + build server, image to GHCR, deploy (gated, see below)
```

## Server (`server/`)

- **Own database.** Unlike router-web, this app does NOT share the CySkor Postgres. It runs its
  own `postgres:16` container and applies its own migrations on start
  (`prisma migrate deploy` in the Dockerfile CMD). So `prisma migrate dev` locally is fine here.
- **Auth:** accounts are created with `pnpm user:create <email> <name> <password>`; there is
  no sign-up route. `POST /api/v1/login` returns an HS256 bearer token signed with
  `AUTH_SECRET`, valid 180 days. Every other route calls `requireUser()`.
- **API** (`src/app/api/v1`): `login`, `me` (GET profile + today's target, PUT profile),
  `foods/search?q=`, `foods/barcode/[code]`, `foods/[id]`, `foods` (POST custom product),
  `diary?date=` (GET day, POST entry), `diary/[id]` (PATCH grams/meal, DELETE), `weight`
  (GET list, POST upserts one per day). Errors are `{ "error": "..." }` with a status code.
  Another user's diary entry answers 404, not 403.
- **Targets** (`src/lib/nutrition.ts`): Mifflin-St Jeor BMR x activity factor = TDEE, minus
  `weeklyLossKg x 7700 / 7`. Floor 1500 kcal (men) / 1200 (women); `clamped: true` tells the
  app the floor, not the plan, set the number. Macros split 25% protein / 30% fat / 45% carbs.
  The target uses the latest weight on or before the day; no profile or no weight = `null`.
- **Diary entries snapshot kcal/macros** at logging time so re-importing a food table never
  rewrites history. PATCHing grams recomputes from the current food row.
- **Custom products are shared** between both users (the point: Cindy's lasagne once, used by
  both). `createdById` records who made it.

### Food data

One `food` table, all values per 100 g, a name column per language (`name_nl/da/de/en`), and
`search_text` (lower-cased brand + all names) with a **pg_trgm GIN index**. Search requires
every word to occur (`ILIKE ALL`) and orders by `word_similarity`, official tables before
custom before Open Food Facts on a tie. Rows are keyed by `(source, source_id)` so every import
is a re-runnable upsert (`upsertFoods()`, one SQL statement per 1000 rows).

| Source | Country | Status |
|---|---|---|
| NEVO (RIVM) | NL | importer done: `pnpm import:nevo <csv>`; finds columns by header |
| Open Food Facts | NL/DK/DE branded | importer done: `pnpm import:off <jsonl.gz>`; plus live barcode lookup |
| Frida (DTU Fødevareinstituttet) | DK | **todo** |
| BLS (Max Rubner-Institut) | DE | **todo**; check the current licence first (older releases were paid) |

- The NEVO importer was tested against a hand-made file shaped like the NEVO export, **not the
  real file**. If it fails on the real export it prints all headers; adjust the regexes in
  `COLUMNS` (`scripts/import-nevo.ts`).
- Open Food Facts: dump at `https://static.openfoodfacts.org/data/openfoodfacts-products.jsonl.gz`
  (~7 GB). The importer streams it and keeps `countries_tags` containing NL/DK/DE. A barcode
  not in the table is fetched live from the OFF API and cached as an `OFF` row. The OFF data is
  ODbL: fine for private use; if this ever ships to others, the OFF-derived part must stay ODbL.
- Plausibility: OFF products without a name, without energy, or above 900 kcal/100 g are
  dropped. kJ-only products are converted (/ 4.184).
- Upserts consume sequence values on conflict, so food ids have gaps. Harmless.

### Running it

```
cd server
cp .env.example .env        # DATABASE_URL, AUTH_SECRET (32+ chars)
pnpm install
pnpm db:migrate             # creates tables + pg_trgm index
pnpm user:create ron@... Ron '<password>'
pnpm dev                    # http://localhost:3002
pnpm test                   # unit tests: targets, OFF mapping
```

## Android (`android/`)

`com.quantumvikings.weightloss`, minSdk 26 / targetSdk 35, Compose + Material 3, Retrofit +
kotlinx.serialization, single activity with navigation-compose. Screens: login, day diary
(date arrows, kcal left, macros, per-meal list with delete), add food (search with debounce,
**barcode scan via Play services code scanner**, so no CAMERA permission, same as valorguard-app),
portion dialog, weight log, profile.

- API base URL is a build field: default `https://food.quantumvikings.com/`, override with
  `./gradlew installDebug -PapiBaseUrl=http://10.0.2.2:3002/` (emulator -> host). Cleartext is
  off in the manifest, so a plain-http local server needs a debug network-security config.
- The token lives in SharedPreferences; any 401 (except on login) signs out.
- UI strings are Dutch and inline for now; move to `strings.xml` (+ `values-da`, `values-de`)
  if Cindy or anyone else wants another language. The food-name language is separate and
  already per user (`profile.locale`).
- **Never compiled yet.** The first version was written in a cloud session without access to
  Google's Maven or the Android SDK. Expect a round of small fixes on the first
  `./gradlew :app:assembleDebug`. Library versions are deliberately conservative (AGP 8.7.3,
  Kotlin 2.1.0, Compose BOM 2024.12.01); let Android Studio upgrade them once it builds.
- When installing, always pass `adb -s <serial>`; a TV box on the LAN also shows up.

## Deployment (planned, not done)

Same pattern as router-web, on the same droplet (`68.183.105.38`):

1. `/opt/weightloss/` with `server/docker-compose.yml` and a `.env` holding
   `POSTGRES_PASSWORD` and `AUTH_SECRET`. Web binds `127.0.0.1:3002` (router-web has 3001),
   Postgres `127.0.0.1:5434`, which is reachable for imports via `ssh -L 5434:127.0.0.1:5434`.
2. The droplet must `docker login ghcr.io` to pull the private image.
3. Caddy: `food.quantumvikings.com { reverse_proxy 127.0.0.1:3002 }` plus a DNS record.
   (The name is a proposal; change it in Caddy and in `android/app/build.gradle.kts`.)
4. CI deploys only when the repository variable `DEPLOY_ENABLED=true` is set and the secrets
   `DEPLOY_SSH_KEY`, `DROPLET_HOST`, `DROPLET_USER` exist.
5. Check free disk on the droplet before the OFF import (a few GB for three countries).

## Privacy

Weight and food diaries are health data (GDPR art. 9). For two household members on our own
server that needs no paperwork, but: no third-party analytics, no sending diaries to external
APIs (an LLM feature would have to use a machine we run, as with Ask in router-web), and
Postgres stays on loopback.

## Next

1. First Android build + fixes; install on both phones.
2. Deploy the server (steps above), import NEVO + OFF, create both accounts.
3. Frida and BLS importers (Danish and German basics, with names in those languages).
4. Weight chart, editing a portion from the diary, recent/favourite foods for fast logging.
5. Water, a fasting timer, then Health Connect (steps/activity -> TDEE adjustment).
6. Recipes made of foods (portion = share of the total), shared between both users.

## Conventions

- Code + docs in English; the UI is Dutch; Ron communicates in Dutch.
- No AI/assistant references in commits or code.
