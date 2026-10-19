import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { formatDay, parseDay } from "@/lib/dates";
import { foodValues, MealEnum, QuickValues, quickValues, toApiEntry } from "@/lib/diary";
import { handler, HttpError, json, parseBody } from "@/lib/http";
import { streakOf } from "@/lib/streak";
import { localeOf, targetFor } from "@/lib/targets";

/**
 * One day: entries per meal, totals and the target for that day, plus the logging streak.
 * `today` is the phone's date; the server runs on UTC and would be a day behind after midnight.
 */
export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const params = new URL(req.url).searchParams;
  const day = parseDay(params.get("date"));
  const today = params.get("today") ? parseDay(params.get("today")) : parseDay(formatDay(new Date()));
  const [entries, target, locale, water, streak] = await Promise.all([
    db.diaryEntry.findMany({ where: { userId, date: day }, include: { food: true }, orderBy: { createdAt: "asc" } }),
    targetFor(userId, day),
    localeOf(userId),
    db.waterEntry.findUnique({ where: { userId_date: { userId, date: day } } }),
    streakOf(userId, today),
  ]);
  const totals = { kcal: 0, protein: 0, carbs: 0, fat: 0, fiber: 0, sugar: 0, saturatedFat: 0, salt: 0 };
  for (const e of entries) {
    totals.kcal += e.kcal;
    totals.protein += e.protein;
    totals.carbs += e.carbs;
    totals.fat += e.fat;
    // Unknown counts as nothing; a quick entry or a sparse product lowers these a little.
    totals.fiber += e.fiber ?? 0;
    totals.sugar += e.sugar ?? 0;
    totals.saturatedFat += e.saturatedFat ?? 0;
    totals.salt += e.salt ?? 0;
  }
  const r = (v: number) => Math.round(v * 10) / 10;
  return json({
    date: formatDay(day),
    entries: entries.map((e) => toApiEntry(e, locale)),
    totals: {
      kcal: Math.round(totals.kcal),
      protein: r(totals.protein),
      carbs: r(totals.carbs),
      fat: r(totals.fat),
      fiber: r(totals.fiber),
      sugar: r(totals.sugar),
      saturatedFat: r(totals.saturatedFat),
      salt: Math.round(totals.salt * 100) / 100,
    },
    target,
    waterMl: water?.ml ?? 0,
    streak,
  });
});

// A food and an amount, or a quick entry: a name and typed-in kcal.
const Body = z.union([
  z.object({ date: z.string(), meal: MealEnum, foodId: z.number().int().positive(), grams: z.number().positive().max(5000) }),
  QuickValues.extend({ date: z.string(), meal: MealEnum }),
]);

export const POST = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, Body);
  let values;
  if ("foodId" in body) {
    const food = await db.food.findUnique({ where: { id: body.foodId } });
    if (!food) throw new HttpError(404, "no such food");
    values = foodValues(food, body.grams);
  } else {
    values = quickValues(body);
  }
  const entry = await db.diaryEntry.create({ data: { userId, date: parseDay(body.date), meal: body.meal, ...values } });
  return json({ id: entry.id }, 201);
});
