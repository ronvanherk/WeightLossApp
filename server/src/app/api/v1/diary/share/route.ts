import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { parseDay } from "@/lib/dates";
import { MealEnum, requireHousehold, scaledValues } from "@/lib/diary";
import { handler, HttpError, json, parseBody } from "@/lib/http";

const Body = z.object({
  date: z.string(),
  meal: MealEnum,
  // Without ids the whole meal goes.
  entryIds: z.array(z.number().int().positive()).max(100).optional(),
  toUserId: z.number().int().positive(),
  // Their portion relative to mine: 0.75 = three quarters of what I had.
  factor: z.number().min(0.1).max(3).default(1),
});

/** Puts my meal (or some of its entries) in a household member's diary: cooked once, logged for both. */
export const POST = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, Body);
  await requireHousehold(userId, body.toUserId);
  const date = parseDay(body.date);
  const source = await db.diaryEntry.findMany({
    where: { userId, date, meal: body.meal, ...(body.entryIds ? { id: { in: body.entryIds } } : {}) },
    include: { food: true },
    orderBy: { createdAt: "asc" },
  });
  if (source.length === 0) throw new HttpError(404, "nothing to share");
  await db.diaryEntry.createMany({
    data: source.map((e) => ({ userId: body.toUserId, date, meal: e.meal, ...scaledValues(e, body.factor) })),
  });
  return json({ shared: source.length });
});
