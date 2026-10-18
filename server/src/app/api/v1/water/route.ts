import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { formatDay, parseDay } from "@/lib/dates";
import { handler, json, parseBody } from "@/lib/http";

export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const date = parseDay(new URL(req.url).searchParams.get("date"));
  const row = await db.waterEntry.findUnique({ where: { userId_date: { userId, date } } });
  return json({ date: formatDay(date), ml: row?.ml ?? 0 });
});

const Body = z.object({ date: z.string(), ml: z.number().int().min(0).max(10_000) });

/** Sets the day's total; the app adds or removes a glass and sends the new total. */
export const PUT = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, Body);
  const date = parseDay(body.date);
  await db.waterEntry.upsert({
    where: { userId_date: { userId, date } },
    create: { userId, date, ml: body.ml },
    update: { ml: body.ml },
  });
  return json({ ok: true });
});
