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
  `foods/search?q=`, `foods/barcode/[code]`, `foods/[id]` (detail: all nutrients in four
  sections, NEVO ingredients, synonyms, favourite flag), `foods/[id]/favorite` (POST/DELETE),
  `foods/favorites`, `foods/recent?meal=` (with `lastGrams`), `foods` (POST custom product),
  `diary?date=&today=` (GET day incl. `waterMl` and `streak`, POST entry), `diary/[id]` (PATCH
  grams/meal, DELETE), `diary/copy` (POST `{from, to, meal?}`: a meal or whole day to another day,
  recomputed from the current food rows), `recipes` (GET all, POST), `recipes/[id]` (GET for
  editing, PUT, DELETE; 409 when logged or used in another recipe), `fasting` (GET running + last
  14, POST start), `fasting/[id]` (PATCH `goalHours`/`startedAt`/`end: true`, DELETE),
  `weight` (GET list, POST upserts one per day), `water` (GET/PUT the day's total ml),
  `steps` (PUT `{date, steps}`, the day's total from Health Connect),
  `diary` POST also takes a quick entry `{date, meal, name, kcal, protein?, carbs?, fat?}`,
  `diary/share` (POST `{date, meal, toUserId, factor, entryIds?}`: my meal into a household
  member's diary at `factor` times my portion), `household` (GET the other members),
  `meals` (GET saved meals, POST `{name, date, meal}` saves that meal's entries), `meals/[id]`
  (DELETE), `meals/[id]/log` (POST `{date, meal}`),
  `summary?from=&to=` (per day kcal, macros, water, steps and that day's target; max 366 days),
  `week?date=&today=` (the Monday-Sunday week holding `date` against the week before: averages
  over logged days, days within target = 70-105% of that day's target, weight as week average
  against week average, waist, fasts), `measurements` (GET `?from=`, PUT one day as a whole;
  missing fields are cleared, all empty deletes the day), `photos` (GET list, POST multipart
  `date` + `photo`, a JPEG of at most 1 MB because nginx allows no more), `photos/[id]` (GET
  the JPEG, owner only; DELETE), `foods/[id]/units` (POST `{name, grams}`) and
  `foods/[id]/units/[unitId]` (DELETE, only by whoever added it). `me` takes `?today=` so the
  weekend shift follows the phone's date. Errors are `{ "error": "..." }` with a status code.
  Another user's diary entry answers 404, not 403.
- **Targets** (`src/lib/nutrition.ts`): Mifflin-St Jeor BMR x activity factor = TDEE, minus
  `weeklyLossKg x 7700 / 7`. Floor 1500 kcal (men) / 1200 (women); `clamped: true` tells the
  app the floor, not the plan, set the number. Macros split 25% protein / 30% fat / 45% carbs.
  The target uses the latest weight on or before the day; no profile or no weight = `null`.
  **Own targets** (profile): `proteinPct/fatPct/carbsPct` replace the split (sum 100, a CHECK);
  `fixedKcal` replaces the computed plan (steps still add, the floor still holds);
  `weekendExtraKcal` adds that on Saturday and Sunday and takes 2/5 of it from each weekday, so
  the week total stays the same (`weekendShift()`, by the day's UTC weekday).
  It also carries limits: fiber at least 14 g per 1000 kcal of TDEE (min 25), total sugars at
  most 18% of the target kcal (the EU reference intake, 90 g at 2000), saturated fat under 10%,
  salt 6 g.
  **Steps** raise it: steps above a baseline for the activity level (sedentary 3000, light 5000,
  moderate 7500, active 10000, very active 12000; the level already counts an ordinary day) earn
  0.0005 kcal x kg body weight each (`stepsKcal()`, reported as `target.stepsKcal`).
- **Diary entries snapshot kcal/macros** (and fiber, sugar, saturated fat, salt; null when the
  food does not say) at logging time so re-importing a food table never rewrites history.
  PATCHing grams recomputes from the current food row. A **quick entry** has no food
  (`food_id` null, `name` set, `grams` 0; a CHECK requires one of the two); PATCH edits its name
  and kcal. Copy, share and saved meals go through `src/lib/diary.ts`.
- **Household**: `app_user.household_id`; users with the same value may share meals
  (`pnpm user:household <email> <email>`). Ron and Cindy are household 1 since 2026-10-19; the
  reviewer account has none. Sharing writes into the other diary but never reads it.
- **Portion units**: `food.serving_label` is the pack's name for `serving_g` ("1 slice"), from
  OFF's `serving_size` (`servingLabel()`); only new imports and live barcode lookups have it,
  the 2026-10-18 import does not until OFF is re-imported. `food_unit` holds named amounts users
  add ("1 boterham" = 35 g), shared like custom products. The app adds rough kitchen measures
  (spoons, glass, cup) itself. Diary entries still store grams only.
- **Body measurements** (`body_measurement`, one row per day) and **progress photos**
  (`progress_photo` + JPEG files under `PHOTO_DIR`, the Docker volume `photos` at
  `/data/photos`, `<userId>/<uuid>.jpg`). Photos never leave the server and are only served to
  their owner; deleting a user removes the rows but not the files.
- **Saved meals** (`saved_meal`, per user): `items` is `[{foodId, grams}]` or quick values;
  logging recomputes from the foods as they are now.
- **Custom products are shared** between both users (the point: Cindy's lasagne once, used by
  both). `createdById` records who made it.
- **Recipes** are `food` rows with source `RECIPE` (`src/lib/recipe-store.ts`): per-100 g values are
  the weighted sum of the ingredients over the cooked weight (or the raw total), `servingG` is one
  portion, `food_ingredient` holds the shares (so the detail screen lists them like NEVO's
  composites) and `details.recipe` keeps `{servings, cookedG, ingredients[{foodId, grams}]}` for
  editing. A detailed nutrient is only summed when every ingredient has it (`combine()`). Editing
  a recipe never touches diary entries (snapshots) or recipes that use it.
- **Streak** (`src/lib/streak.ts`): days in a row with an entry, up to `today` from the phone
  (the server runs on UTC); yesterday counts while today is still empty.
- **Fasting**: table `fast`, one open row (`ended_at` null) per user at most.
- **Search language**: foods where every query word occurs in the user's own language (name in
  `profile.locale`, brand, synonyms) rank first, then the general similarity order.

### Food data

One `food` table, all values per 100 g, a name column per language (`name_nl/da/de/en`), and
`search_text` (lower-cased brand + all names) with a **pg_trgm GIN index**. Search requires
every word to occur (`ILIKE ALL`) and orders by `word_similarity`, official tables before
custom before Open Food Facts on a tie. Rows are keyed by `(source, source_id)` so every import
is a re-runnable upsert (`upsertFoods()`, one SQL statement per 1000 rows).

| Source | Country | Status |
|---|---|---|
| NEVO (RIVM) | NL | **imported 2026-10-18**: v2025/9.0, 2,328 foods, all 137 nutrients, 695 recipes; `pnpm import:nevo <folder>` |
| Open Food Facts | NL/DK/DE branded | **imported 2026-10-18** (321k); `pnpm import:off <jsonl.gz>`; plus live barcode lookup |
| Frida (DTU Fødevareinstituttet) | DK | **imported 2026-10-19**: FCDB v6.1 (May 2026), 1,390 foods, CC BY 4.0; `pnpm import:frida <xlsx>` |
| BLS (Max Rubner-Institut) | DE | **imported 2026-10-19**: BLS 4.0 (2025), 7,140 foods, CC BY 4.0 (free since Dec 2025); `pnpm import:bls <xlsx>` |

- NEVO is a free download (form at https://www.rivm.nl/form/nevo-online-gegevensbestand-2):
  a folder of `|`-separated CSVs. The importer reads the main file (every `CODE (unit)` column
  into `food.nutrients`, food group into `category`, synonyms/remarks/traces/fortification into
  `details`), `_Nutrienten_Nutrients.csv` into `nutrient` (it is a tree with repeated codes; the
  first occurrence wins) and `_Recepten_Recipes.csv` into `food_ingredient` (12 lines point at
  codes not in the release and are skipped). The CSVs live on the server in
  `/opt/weightloss-data/nevo`. Attribution shown in the app: "NEVO-online versie 2025/9.0, RIVM".
- Frida: https://doi.org/10.11583/DTU.32312844, file `FCDB_6.1_Dataset.xlsx` (sheets Food,
  Parameter, Data_Normalised). BLS: https://www.blsdb.de/assets/uploads/BLS_4_0_2025_DE.zip, file
  `BLS_4_0_Daten_2025_DE.xlsx` (per nutrient three columns: value, origin, reference; markers `-`
  unknown, `TR`/`<LOD`/`<LOQ` = 0). Both use EuroFIR component codes like NEVO, so values land in
  `food.nutrients` under NEVO's codes with units converted (`src/lib/composition.ts`); codes NEVO
  lacks are dropped. Names: Frida da+en, BLS de+en; `category` is the English food group (the app
  maps both Dutch and English groups to an emoji). The xlsx files live on the server in
  `/opt/weightloss-data/{frida,bls}`. Attribution in the app's source line: "© The Danish Food
  Composition Database (fcdb.fooddata.dk), version 6.1, May 2026, DTU" and "Max Rubner-Institut,
  BLS 4.0". Neither has portion sizes.
- Open Food Facts: dump at `https://static.openfoodfacts.org/data/openfoodfacts-products.jsonl.gz`
  (~7 GB). The importer streams it and keeps `countries_tags` containing NL/DK/DE. A barcode
  not in the table is fetched live from the OFF API and cached as an `OFF` row. The OFF data is
  ODbL: fine for private use; if this ever ships to others, the OFF-derived part must stay ODbL.
- **OFF changed its format in 2025/26:** in the dump `nutriments` is nearly empty and the values
  sit in `nutrition.aggregated_set.nutrients.<name>.value` (per `100g`/`100ml`). `offToFood` reads
  both. First full import 2026-10-18: 321,377 of 4.8 M products kept (names: 199k de, 41k nl,
  12k da), DB 159 MB, ~30 min on the web server. The dump is now **13 GB**, not 7; it was
  deleted afterwards (server disk 84%). Re-run with `/opt/weightloss-data/import-off.sh` in tmux.
- Plausibility: OFF products without a name, without energy, or above 900 kcal/100 g are
  dropped. kJ-only products are converted (/ 4.184). Values are rounded to 2 decimals (the dump
  has float noise like 9.100000381); existing rows were rounded in SQL on 2026-10-19.
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
kotlinx.serialization, single activity with navigation-compose. Look and feel modelled on
Yazio (2026-10-18): own theme in `ui/theme/Theme.kt` (green/teal gradient headers, fixed colours
per macro and per meal, light status bar icons everywhere), bottom navigation Dagboek /
Voortgang / Profiel. Screens: login; diary (week strip, calorie ring, macro bars, meal cards with
an advised share of the day 25/35/30/10 %, water glasses, bottom sheet to edit/move/delete an
entry, "same as yesterday" on an empty meal, a fasting card on today with goal chips, live
progress and a start-time picker, a 🔥 streak pill in the header from 2 days, a steps card that
connects Health Connect); add food (recent,
favourites, saved meals and recipes tabs, "only calories" quick entry, search, one-tap add with last/serving amount,
**barcode scan via Play services code scanner**, so no CAMERA permission); food detail (portion
presets, macro donut, nutrition table, collapsible NEVO nutrient sections, ingredients,
favourite heart, source line, edit button on recipes); own product; recipe editor (name,
portions, optional cooked weight, ingredients from a search dialog, live per-portion values); progress
(also steps and fasting charts once there is data) (weight line chart with goal, BMI, 7-day
calorie and water bars, history); profile (target card, date picker). Shared pieces in
`ui/Components.kt`; food-group emoji in `categoryEmoji()`.

- API base URL is a build field: default `https://food.quantumvikings.com/`, override with
  `./gradlew installDebug -PapiBaseUrl=http://10.0.2.2:3002/` (emulator -> host). Cleartext is
  off in the manifest, so a plain-http local server needs a debug network-security config.
- The token lives in SharedPreferences; any 401 (except on login) signs out.
- **Health Connect** (`data/HealthSteps.kt`, connect-client 1.1.0, which needs AGP 8.9.1+): only
  `READ_STEPS`. The diary reads the shown day's total, PUTs it to `/steps` and then loads the day,
  so the target already includes it; it reloads on every resume. The manifest has the rationale
  activity (`PermissionsRationaleActivity`, opens the privacy policy) and the Android 14
  `VIEW_PERMISSION_USAGE` alias that Health Connect requires. Play needs the Health Connect
  declaration in App content for every release that requests a health permission.
- **Reminders** (`data/Reminders.kt`): breakfast, lunch, dinner, weigh-in and water, each on/off
  with a time in Profile, stored in SharedPreferences. Inexact daily alarms; `ReminderReceiver`
  re-arms tomorrow first, then asks the server (diary / weights of today) and only notifies when
  the meal is empty, there is no weight, or water is behind 2 l spread over 08:00-22:00.
  Re-armed on app start and after boot.
- **Own targets** sit in a "Calorieën en macro's" card in Profile; `ProfileDto` has those fields
  without defaults on purpose (kotlinx omits default values and the server keeps what it does
  not get). Product screen: amount × measure (g/ml, the pack's portion, users' units, spoons or
  glass) and "+ Eenheid". Progress: a week card that opens `WeekScreen`, a measurements card
  (chips per measure, line chart, waist-to-height) and a photos card (system photo picker, so
  no permission; `data/Photos.kt` shrinks to 1600 px and under 900 KB). The weekly reminder
  (`Reminders.Kind.WEEK`) fires on Sunday and opens the week screen via the `open` extra.
- **Widget** (`widget/DayWidget.kt`, Glance 1.1.1): kcal left today with a bar, water and a
  "+ glas" button; fetches the day itself, refreshed by the app after the diary loads or water
  changes, and every 30 minutes.
- **Fasting notification** (`data/FastingAlarm.kt`): an inexact `setAndAllowWhileIdle` alarm at
  start + goal, re-armed whenever the diary loads the fast and after a reboot (`BootReceiver`);
  `POST_NOTIFICATIONS` is asked when a fast is started. No exact-alarm permission on purpose.
- UI strings are Dutch and inline for now; move to `strings.xml` (+ `values-da`, `values-de`)
  if Cindy or anyone else wants another language. The food-name language is separate and
  already per user (`profile.locale`).
- **Built by CI**, not locally: `.github/workflows/android.yml` runs
  `./gradlew :app:assembleDebug` on every push touching `android/` and attaches
  `weightloss-debug-apk` (30 days) to the run. Cloud sessions cannot reach Google's Maven or
  the Android SDK, so this is how they compile; read the job log for errors. First build green
  on 2026-10-17 without changes. Library versions are deliberately conservative (AGP 8.7.3,
  Kotlin 2.1.0, Compose BOM 2024.12.01); upgrade them in one go, not piecemeal.
- **Google Play closed testing** (set up 2026-10-18). CI builds `bundleRelease` on every push,
  signed with the upload key (secrets `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`; the
  key itself and its password are in `~/Documents/WeightLossApp-keys/`, outside the repo),
  versionCode = run number + 100, versionName `0.2.<code>`, artifact `weightloss-release-aab`.
  Upload to the `alpha` (closed testing) track is gated on variable `PLAY_UPLOAD_ENABLED=true`
  plus secret `PLAY_SERVICE_ACCOUNT_JSON`. Target SDK 36 (Play minimum since 2026-08-31) on
  AGP 8.7 with `android.suppressUnsupportedCompileSdk=36`. Privacy policy for the Play listing:
  https://quantumvikings.com/weightloss/privacy.html (English; source in the quantumvikings.com
  site repo, `weightloss/privacy.html`, deployed with its `deploy.sh`). The Dutch
  `server/src/app/privacy/page.tsx` stays as a second copy; keep both in step.
  Testers: Ron (vanherkhoek@gmail.com) and Cindy (evilkayleigh@gmail.com, her Android account).
  Store listing assets and texts in `play-store/` (icon and feature graphic rendered from
  `icon.svg`/`feature.html` with headless Chrome; screenshots taken from the reviewer account).
  Reviewer account for Play review: `review@quantumvikings.com` (user 3, demo data only; password
  in `~/Documents/WeightLossApp-keys/README.txt`). Never take store screenshots from Ron's or
  Cindy's account. Tester list in the Console: "WeightLoss testers"; countries NL, DK, DE.
- Debug builds are `com.quantumvikings.weightloss.debug` ("WeightLoss dev"), signed with the
  committed `app/debug.keystore`, so they install next to the Play version and over each other.
- When installing, always pass `adb -s <serial>`; a TV box on the LAN also shows up.

## Deployment

Runs on the general web server **`192.168.68.149`** (Ubuntu 24.04, public `95.154.21.210`,
`ssh ron@192.168.68.149`, passwordless sudo), not on the droplet. Live since 2026-10-17.

- `/opt/weightloss` is a git checkout of this (public) repo; the stack is `server/docker-compose.yml`,
  project name `weightloss`, secrets in `server/.env` (mode 600, `POSTGRES_PASSWORD`,
  `AUTH_SECRET`, `OFF_USER_AGENT`). The image is **built on the server** (`network: host`, the
  daemon's DNS is unreachable from build containers), not pulled from GHCR.
- Ports: web `127.0.0.1:3020` (3002 there belongs to the Amazing gateway), Postgres
  `127.0.0.1:5434`, for imports via `ssh -L 5434:127.0.0.1:5434 ron@192.168.68.149`.
- nginx vhost `/etc/nginx/sites-available/food.quantumvikings.com.conf` (symlinked into
  `sites-enabled`), proxy to 3020; TLS via `certbot --nginx` like the other vhosts.
  DNS for quantumvikings.com is at Cloudflare: an A record `food` -> `95.154.21.210`, DNS only.
- Update: `ssh ron@192.168.68.149 'cd /opt/weightloss && git pull && cd server && sudo docker compose -p weightloss up -d --build'`.
- The CI `deploy` job (droplet + GHCR) is unused; the server sits behind NAT on the LAN.
- Scripts (`user:create`, imports) are not in the runtime image; run them from the checkout in a
  throwaway container on the host network (DB at `127.0.0.1:5434`, password from `.env`):
  `sudo docker run --rm --network host -v "$PWD":/app -w /app -e DATABASE_URL=... node:22-alpine sh -c "corepack enable && pnpm install --frozen-lockfile && pnpm exec prisma generate && pnpm user:create ..."`
  Accounts exist for Ron (id 1) and Cindy (id 2) since 2026-10-17; TLS by certbot the same day.
- Disk: ~29 GB free on 2026-10-17; the OFF import needs a few GB for three countries.

## Privacy

Weight and food diaries are health data (GDPR art. 9). For two household members on our own
server that needs no paperwork, but: no third-party analytics, no sending diaries to external
APIs (an LLM feature would have to use a machine we run, as with Ask in router-web), and
Postgres stays on loopback.

## Next

Done 2026-10-19: recipes, copy a meal, fasting timer, streaks, Frida + BLS, OFF rounding.

Done 2026-10-19 (afternoon): Health Connect steps, fasting notification and history, own-language
search first.

Done 2026-10-19 (evening): quick entries, saved meals, sharing a meal within the household,
reminders, fiber/sugar/saturated fat/salt targets, home screen widget.

Done 2026-10-20: own targets (split, fixed kcal, weekend), body measurements and progress photos,
weekly review with a Sunday notification, portion units.

1. Exercise sessions from Health Connect (cycling, gym) next to steps (new Health permission:
   Play declaration first).
2. Weight from Health Connect (smart scale); dark mode; CSV/PDF export.
3. Re-import OFF so existing products get `serving_label` (13 GB dump, server disk ~90%).
4. UI strings to `strings.xml` only if someone needs another language (Cindy is Dutch).

## Conventions

- Code + docs in English; the UI is Dutch; Ron communicates in Dutch.
- No AI/assistant references in commits or code.
