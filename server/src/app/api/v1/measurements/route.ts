import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { formatDay, parseDay } from "@/lib/dates";
import { handler, json, parseBody } from "@/lib/http";

const FIELDS = ["waistCm", "hipCm", "chestCm", "thighCm", "armCm", "neckCm", "bodyFatPct"] as const;

/** Measurements from `from` (default: a year ago), oldest first. */
export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const raw = new URL(req.url).searchParams.get("from");
  const from = raw ? parseDay(raw) : new Date(Date.now() - 365 * 86_400_000);
  const rows = await db.bodyMeasurement.findMany({ where: { userId, date: { gte: from } }, orderBy: { date: "asc" } });
  return json({
    measurements: rows.map((m) => ({ date: formatDay(m.date), ...Object.fromEntries(FIELDS.map((f) => [f, m[f]])) })),
  });
});

const cm = z.number().min(10).max(250).nullable().optional();
const Body = z.object({
  date: z.string(),
  waistCm: cm,
  hipCm: cm,
  chestCm: cm,
  thighCm: cm,
  armCm: cm,
  neckCm: cm,
  bodyFatPct: z.number().min(3).max(70).nullable().optional(),
});

/** The day's measurements as a whole: a missing or null field is cleared; all empty deletes the day. */
export const PUT = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, Body);
  const date = parseDay(body.date);
  const data = Object.fromEntries(FIELDS.map((f) => [f, body[f] ?? null])) as Record<(typeof FIELDS)[number], number | null>;
  if (FIELDS.every((f) => data[f] === null)) {
    await db.bodyMeasurement.deleteMany({ where: { userId, date } });
    return json({ ok: true });
  }
  await db.bodyMeasurement.upsert({
    where: { userId_date: { userId, date } },
    create: { userId, date, ...data },
    update: data,
  });
  return json({ ok: true });
});
