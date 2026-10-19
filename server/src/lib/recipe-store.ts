import { randomUUID } from "node:crypto";
import { z } from "zod";
import { db } from "./db";
import { displayName, searchText, toApi } from "./foods";
import { HttpError } from "./http";
import { combine, type RecipeSpec } from "./recipes";

export const RecipeBody = z.object({
  name: z.string().trim().min(1).max(200),
  servings: z.number().int().min(1).max(50),
  cookedG: z.number().positive().max(50_000).nullable().optional(),
  ingredients: z
    .array(z.object({ foodId: z.number().int().positive(), grams: z.number().positive().max(10_000) }))
    .min(1)
    .max(60),
});

/** Creates the recipe, or rewrites recipe `id`: values, ingredient rows and the spec in `details`. */
export async function saveRecipe(userId: number, locale: string, body: z.infer<typeof RecipeBody>, id?: number) {
  // The same food twice is one ingredient with the grams added up.
  const grams = new Map<number, number>();
  for (const i of body.ingredients) grams.set(i.foodId, (grams.get(i.foodId) ?? 0) + i.grams);
  if (id !== undefined && grams.has(id)) throw new HttpError(400, "a recipe cannot contain itself");
  const foods = await db.food.findMany({ where: { id: { in: [...grams.keys()] } } });
  if (foods.length !== grams.size) throw new HttpError(400, "unknown ingredient");
  const parts = foods.map((food) => ({ food, grams: grams.get(food.id)! }));
  const c = combine(parts, body.cookedG ?? null);
  if (c.kcal > 900) throw new HttpError(400, "more than 900 kcal per 100 g; check the cooked weight");

  const spec: RecipeSpec = {
    servings: body.servings,
    cookedG: body.cookedG ?? null,
    ingredients: parts.map((p) => ({ foodId: p.food.id, grams: p.grams })),
  };
  const names = { nameNl: null, nameDa: null, nameDe: null, nameEn: null } as Record<string, string | null>;
  names[{ nl: "nameNl", da: "nameDa", de: "nameDe" }[locale] ?? "nameNl"] = body.name;
  // Some ingredients measured in ml make this approximate; 1 ml counts as 1 g.
  const data = {
    ...names,
    kcal: c.kcal,
    protein: c.protein,
    carbs: c.carbs,
    fat: c.fat,
    fiber: c.fiber,
    sugar: c.sugar,
    salt: c.salt,
    saturatedFat: c.saturatedFat,
    nutrients: c.nutrients ?? undefined,
    servingG: Math.round(((body.cookedG ?? c.rawG) / body.servings) * 10) / 10,
    category: "Recept",
    details: { recipe: spec } as object,
    searchText: searchText({ ...names }),
  };
  const ingredientRows = parts.map((p, i) => ({ ingredientId: p.food.id, share: c.shares[i] }));
  const food = await db.$transaction(async (tx) => {
    if (id === undefined) {
      return tx.food.create({
        data: { source: "RECIPE", sourceId: randomUUID(), createdById: userId, ...data, ingredients: { create: ingredientRows } },
      });
    }
    await tx.foodIngredient.deleteMany({ where: { foodId: id } });
    return tx.food.update({ where: { id }, data: { ...data, ingredients: { create: ingredientRows } } });
  });
  return toApi(food, locale);
}

export async function recipeOrThrow(id: number) {
  const food = await db.food.findUnique({ where: { id } });
  if (!food || food.source !== "RECIPE") throw new HttpError(404, "no such recipe");
  return food;
}

/** The spec with ingredient names, for the edit screen. */
export async function recipeForEdit(id: number, locale: string) {
  const food = await recipeOrThrow(id);
  const spec = (food.details as unknown as { recipe: RecipeSpec }).recipe;
  const foods = await db.food.findMany({ where: { id: { in: spec.ingredients.map((i) => i.foodId) } } });
  const byId = new Map(foods.map((f) => [f.id, f]));
  return {
    id: food.id,
    name: displayName(food, locale),
    servings: spec.servings,
    cookedG: spec.cookedG,
    food: toApi(food, locale),
    ingredients: spec.ingredients
      .filter((i) => byId.has(i.foodId))
      .map((i) => ({ grams: i.grams, food: toApi(byId.get(i.foodId)!, locale) })),
  };
}
