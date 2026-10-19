import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { formatDay, parseDay } from "@/lib/dates";
import { displayName } from "@/lib/foods";
import { handler, HttpError, json, parseBody } from "@/lib/http";
import { portion } from "@/lib/nutrition";
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
  const totals = { kcal: 0, protein: 0, carbs: 0, fat: 0 };
  for (const e of entries) {
    totals.kcal += e.kcal;
    totals.protein += e.protein;
    totals.carbs += e.carbs;
    totals.fat += e.fat;
  }
  const r = (v: number) => Math.round(v * 10) / 10;
  return json({
    date: formatDay(day),
    entries: entries.map((e) => ({
      id: e.id,
      meal: e.meal,
      foodId: e.foodId,
      name: displayName(e.food, locale),
      brand: e.food.brand,
      category: e.food.category,
      unit: e.food.unit,
      grams: e.grams,
      kcal: e.kcal,
      protein: e.protein,
      carbs: e.carbs,
      fat: e.fat,
    })),
    totals: { kcal: Math.round(totals.kcal), protein: r(totals.protein), carbs: r(totals.carbs), fat: r(totals.fat) },
    target,
    waterMl: water?.ml ?? 0,
    streak,
  });
});

const Body = z.object({
  date: z.string(),
  meal: z.enum(["BREAKFAST", "LUNCH", "DINNER", "SNACK"]),
  foodId: z.number().int().positive(),
  grams: z.number().positive().max(5000),
});

export const POST = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, Body);
  const food = await db.food.findUnique({ where: { id: body.foodId } });
  if (!food) throw new HttpError(404, "no such food");
  const entry = await db.diaryEntry.create({
    data: { userId, date: parseDay(body.date), meal: body.meal, foodId: food.id, grams: body.grams, ...portion(food, body.grams) },
  });
  return json({ id: entry.id }, 201);
});
