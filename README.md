# WeightLossApp

Calorie and weight tracker for our household: a native Android app backed by a small API
server with a food database for the Netherlands, Denmark and Germany.

- `server/` — Next.js + Prisma API and Postgres (see `server/.env.example`)
- `android/` — Kotlin + Jetpack Compose app

Setup, design decisions and status: [`CLAUDE.md`](CLAUDE.md).

Food data: NEVO (RIVM), Open Food Facts (ODbL); Frida (DTU) and BLS (MRI) planned.
