import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { parseDay } from "@/lib/dates";
import { handler, json, parseBody } from "@/lib/http";

const Body = z.object({ date: z.string(), steps: z.number().int().min(0).max(150_000) });

/** Sets the day's step count; the app reads it from Health Connect and sends the total. */
export const PUT = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, Body);
  const date = parseDay(body.date);
  await db.dailySteps.upsert({
    where: { userId_date: { userId, date } },
    create: { userId, date, steps: body.steps },
    update: { steps: body.steps },
  });
  return json({ ok: true });
});
