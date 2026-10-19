import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { parseDay } from "@/lib/dates";
import { MealEnum, SavedItem, toSavedItem } from "@/lib/diary";
import { displayName } from "@/lib/foods";
import { handler, HttpError, json, parseBody } from "@/lib/http";
import { portion } from "@/lib/nutrition";
import { localeOf } from "@/lib/targets";

/** This user's saved meals with their items and current kcal; newest first. */
export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const [meals, locale] = await Promise.all([
    db.savedMeal.findMany({ where: { userId }, orderBy: { createdAt: "desc" } }),
    localeOf(userId),
  ]);
  const parsed = meals.map((m) => ({ meal: m, items: z.array(SavedItem).catch([]).parse(m.items) }));
  const ids = parsed.flatMap((p) => p.items.flatMap((i) => ("foodId" in i ? [i.foodId] : [])));
  const foods = new Map((await db.food.findMany({ where: { id: { in: ids } } })).map((f) => [f.id, f]));
  return json({
    meals: parsed.map(({ meal, items }) => {
      const lines = items.flatMap((i): { name: string; grams: number | null; unit: string; kcal: number }[] => {
        if (!("foodId" in i)) return [{ name: i.name, grams: null, unit: "g", kcal: Math.round(i.kcal) }];
        const food = foods.get(i.foodId);
        return food ? [{ name: displayName(food, locale), grams: i.grams, unit: food.unit, kcal: portion(food, i.grams).kcal }] : [];
      });
      return { id: meal.id, name: meal.name, kcal: lines.reduce((s, l) => s + l.kcal, 0), items: lines };
    }),
  });
});

const Body = z.object({
  name: z.string().trim().min(1).max(60),
  // The entries of this meal on this day become the saved meal.
  date: z.string(),
  meal: MealEnum,
});

export const POST = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, Body);
  const entries = await db.diaryEntry.findMany({
    where: { userId, date: parseDay(body.date), meal: body.meal },
    orderBy: { createdAt: "asc" },
  });
  if (entries.length === 0) throw new HttpError(400, "Deze maaltijd is nog leeg.");
  const saved = await db.savedMeal.create({ data: { userId, name: body.name, items: entries.map(toSavedItem) } });
  return json({ id: saved.id }, 201);
});
