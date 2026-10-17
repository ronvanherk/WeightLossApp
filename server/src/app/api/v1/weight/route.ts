import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { formatDay, parseDay } from "@/lib/dates";
import { handler, json, parseBody } from "@/lib/http";

/** Weights from `from` (default: 90 days ago) up to today, oldest first. */
export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const raw = new URL(req.url).searchParams.get("from");
  const from = raw ? parseDay(raw) : new Date(Date.now() - 90 * 86_400_000);
  const rows = await db.weightEntry.findMany({ where: { userId, date: { gte: from } }, orderBy: { date: "asc" } });
  return json({ weights: rows.map((w) => ({ date: formatDay(w.date), weightKg: w.weightKg })) });
});

const Body = z.object({ date: z.string(), weightKg: z.number().min(30).max(300) });

/** One weight per day; posting the same day again replaces it. */
export const POST = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, Body);
  const date = parseDay(body.date);
  await db.weightEntry.upsert({
    where: { userId_date: { userId, date } },
    create: { userId, date, weightKg: body.weightKg },
    update: { weightKg: body.weightKg },
  });
  return json({ ok: true });
});
