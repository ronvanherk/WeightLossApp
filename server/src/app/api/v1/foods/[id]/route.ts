import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { displayName, toApi, type FoodDetails } from "@/lib/foods";
import { handler, HttpError, json, parseId } from "@/lib/http";
import { localeOf } from "@/lib/targets";

/** One food with everything known about it: all nutrients by group, ingredients, extras. */
export const GET = handler(async (req: Request, ctx: { params: Promise<{ id: string }> }) => {
  const userId = await requireUser(req);
  const id = parseId((await ctx.params).id);
  const [food, locale, definitions, favorite] = await Promise.all([
    db.food.findUnique({
      where: { id },
      include: { ingredients: { include: { ingredient: true }, orderBy: { share: "desc" } } },
    }),
    localeOf(userId),
    db.nutrient.findMany({ orderBy: { sortOrder: "asc" } }),
    db.favorite.findUnique({ where: { userId_foodId: { userId, foodId: id } } }),
  ]);
  if (!food) throw new HttpError(404, "no such food");

  const values = (food.nutrients ?? {}) as Record<string, number>;
  const groups: { name: string; nutrients: { code: string; name: string; value: number; unit: string }[] }[] = [];
  for (const d of definitions) {
    if (values[d.code] === undefined) continue;
    let group = groups.find((g) => g.name === d.groupNl);
    if (!group) groups.push((group = { name: d.groupNl, nutrients: [] }));
    group.nutrients.push({ code: d.code, name: d.nameNl, value: values[d.code], unit: d.unit });
  }
  const details = (food.details ?? {}) as FoodDetails;
  // Traces and fortification are lists of nutrient codes; show their names.
  const names = (codes?: string) =>
    codes?.split(",").map((c) => definitions.find((d) => d.code === c.trim())?.nameNl ?? c.trim());

  return json({
    food: toApi(food, locale),
    favorite: favorite !== null,
    nutrientGroups: groups,
    ingredients: food.ingredients.map((i) => ({
      foodId: i.ingredientId,
      name: displayName(i.ingredient, locale),
      share: i.share,
    })),
    synonyms: details.synonyms ?? [],
    remark: details.remark ?? null,
    traces: names(details.traces) ?? [],
    fortified: names(details.fortified) ?? [],
  });
});
