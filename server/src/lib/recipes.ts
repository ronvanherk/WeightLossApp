import type { Food } from "@prisma/client";

/** What a recipe stores in `food.details.recipe`, so it can be edited again. */
export interface RecipeSpec {
  servings: number;
  /** Weight of the finished dish, when cooking changes it (pasta absorbs water, sauce reduces). */
  cookedG: number | null;
  ingredients: { foodId: number; grams: number }[];
}

type Values = Pick<Food, "kcal" | "protein" | "carbs" | "fat" | "fiber" | "sugar" | "salt" | "saturatedFat" | "nutrients">;

const OPTIONAL = ["protein", "carbs", "fat", "fiber", "sugar", "salt", "saturatedFat"] as const;
const r = (v: number, digits: number) => Math.round(v * 10 ** digits) / 10 ** digits;

/**
 * Per-100 g values of a dish made of `parts`, over its cooked weight when given (else the raw total).
 * A macro missing in every ingredient stays null; missing in some counts as 0 there.
 * A detailed nutrient (NEVO code) is only kept when every ingredient has it, so a sum never looks
 * complete when it is not.
 */
export function combine(parts: { food: Values; grams: number }[], cookedG: number | null) {
  const raw = parts.reduce((s, p) => s + p.grams, 0);
  const weight = cookedG ?? raw;
  const f = (grams: number) => grams / weight; // share of 100 g of dish per 100 g of ingredient
  const kcal = parts.reduce((s, p) => s + p.food.kcal * f(p.grams), 0);
  const macros = Object.fromEntries(
    OPTIONAL.map((k) => [
      k,
      parts.every((p) => p.food[k] === null) ? null : r(parts.reduce((s, p) => s + (p.food[k] ?? 0) * f(p.grams), 0), 2),
    ]),
  ) as Record<(typeof OPTIONAL)[number], number | null>;
  const maps = parts.map((p) => (p.food.nutrients ?? {}) as Record<string, number>);
  const nutrients: Record<string, number> = {};
  for (const code of Object.keys(maps[0] ?? {})) {
    if (maps.every((m) => typeof m[code] === "number")) {
      nutrients[code] = r(maps.reduce((s, m, i) => s + m[code] * f(parts[i].grams), 0), 3);
    }
  }
  return {
    kcal: r(kcal, 1),
    ...macros,
    nutrients: Object.keys(nutrients).length > 0 ? nutrients : null,
    rawG: raw,
    /** Percent of the raw weight per ingredient, for food_ingredient.share. */
    shares: parts.map((p) => r((p.grams / raw) * 100, 2)),
  };
}
