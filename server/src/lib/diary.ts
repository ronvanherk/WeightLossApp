import type { DiaryEntry, Food, Meal, Prisma } from "@prisma/client";
import { z } from "zod";
import { db } from "./db";
import { displayName } from "./foods";
import { HttpError } from "./http";
import { portion, portionExtras } from "./nutrition";

export const MealEnum = z.enum(["BREAKFAST", "LUNCH", "DINNER", "SNACK"]);

/** Values typed in for a quick entry; also how a quick entry is kept in a saved meal. */
export const QuickValues = z.object({
  name: z.string().trim().min(1).max(80),
  kcal: z.number().min(0).max(10000),
  protein: z.number().min(0).max(1000).optional(),
  carbs: z.number().min(0).max(1000).optional(),
  fat: z.number().min(0).max(1000).optional(),
});
export type QuickValues = z.infer<typeof QuickValues>;

/** One line of a saved meal: a food and an amount, or a quick entry. */
export const SavedItem = z.union([z.object({ foodId: z.number().int().positive(), grams: z.number().positive().max(5000) }), QuickValues]);
export type SavedItem = z.infer<typeof SavedItem>;

type EntryWithFood = DiaryEntry & { food: Food | null };

/** The columns of a diary entry besides who, when and which meal. */
export type EntryValues = Pick<
  DiaryEntry,
  "foodId" | "name" | "grams" | "kcal" | "protein" | "carbs" | "fat" | "fiber" | "sugar" | "saturatedFat" | "salt"
>;

const r1 = (v: number) => Math.round(v * 10) / 10;

/** The snapshot columns for a food portion. */
export function foodValues(food: Food, grams: number): EntryValues {
  return { foodId: food.id, name: null, grams, ...portion(food, grams), ...portionExtras(food, grams) };
}

export function quickValues(q: QuickValues): EntryValues {
  return {
    foodId: null,
    name: q.name,
    grams: 0,
    kcal: Math.round(q.kcal),
    protein: r1(q.protein ?? 0),
    carbs: r1(q.carbs ?? 0),
    fat: r1(q.fat ?? 0),
    fiber: null,
    sugar: null,
    saturatedFat: null,
    salt: null,
  };
}

/**
 * An entry again, `factor` times as much: copying a meal to another day, or to the other
 * household member with their own portion. Food entries are recomputed from the food as it is now.
 */
export function scaledValues(e: EntryWithFood, factor = 1): EntryValues {
  if (e.food) return foodValues(e.food, Math.round(e.grams * factor));
  const s = (v: number | null) => (v == null ? null : r1(v * factor));
  return {
    foodId: null,
    name: e.name,
    grams: 0,
    kcal: Math.round(e.kcal * factor),
    protein: r1(e.protein * factor),
    carbs: r1(e.carbs * factor),
    fat: r1(e.fat * factor),
    fiber: s(e.fiber),
    sugar: s(e.sugar),
    saturatedFat: s(e.saturatedFat),
    salt: e.salt == null ? null : Math.round(e.salt * factor * 100) / 100,
  };
}

/** How a saved meal remembers an entry. */
export function toSavedItem(e: DiaryEntry): SavedItem {
  if (e.foodId != null) return { foodId: e.foodId, grams: e.grams };
  return { name: e.name ?? "?", kcal: e.kcal, protein: e.protein, carbs: e.carbs, fat: e.fat };
}

/** Saved-meal items as diary rows for one day and meal; foods that no longer exist are skipped. */
export async function entriesFromItems(
  userId: number,
  date: Date,
  meal: Meal,
  items: SavedItem[],
  factor = 1,
): Promise<Prisma.DiaryEntryCreateManyInput[]> {
  const ids = items.flatMap((i) => ("foodId" in i ? [i.foodId] : []));
  const foods = new Map((await db.food.findMany({ where: { id: { in: ids } } })).map((f) => [f.id, f]));
  return items.flatMap((i): Prisma.DiaryEntryCreateManyInput[] => {
    if ("foodId" in i) {
      const food = foods.get(i.foodId);
      return food ? [{ userId, date, meal, ...foodValues(food, Math.round(i.grams * factor)) }] : [];
    }
    const q = { ...i, kcal: i.kcal * factor, protein: (i.protein ?? 0) * factor, carbs: (i.carbs ?? 0) * factor, fat: (i.fat ?? 0) * factor };
    return [{ userId, date, meal, ...quickValues(q) }];
  });
}

export function toApiEntry(e: EntryWithFood, locale: string) {
  return {
    id: e.id,
    meal: e.meal,
    foodId: e.foodId,
    name: e.food ? displayName(e.food, locale) : (e.name ?? "?"),
    brand: e.food?.brand ?? null,
    category: e.food?.category ?? null,
    unit: e.food?.unit ?? "g",
    grams: e.grams,
    kcal: e.kcal,
    protein: e.protein,
    carbs: e.carbs,
    fat: e.fat,
    fiber: e.fiber,
    sugar: e.sugar,
    saturatedFat: e.saturatedFat,
    salt: e.salt,
  };
}

/** The other members of this user's household; empty when the user has none. */
export async function householdOf(userId: number) {
  const me = await db.user.findUnique({ where: { id: userId }, select: { householdId: true } });
  if (me?.householdId == null) return [];
  return db.user.findMany({
    where: { householdId: me.householdId, id: { not: userId } },
    select: { id: true, name: true },
    orderBy: { id: "asc" },
  });
}

/** Throws unless `otherId` is in this user's household. */
export async function requireHousehold(userId: number, otherId: number) {
  const members = await householdOf(userId);
  const other = members.find((m) => m.id === otherId);
  // Someone outside the household is reported as missing, as with entries.
  if (!other) throw new HttpError(404, "no such household member");
  return other;
}
