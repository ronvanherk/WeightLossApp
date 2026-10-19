import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { toApi } from "@/lib/foods";
import { handler, json, parseBody } from "@/lib/http";
import { RecipeBody, saveRecipe } from "@/lib/recipe-store";
import { localeOf } from "@/lib/targets";

/** All recipes, shared between the users; newest first. */
export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const [foods, locale] = await Promise.all([
    db.food.findMany({ where: { source: "RECIPE" }, orderBy: { updatedAt: "desc" } }),
    localeOf(userId),
  ]);
  return json({ foods: foods.map((f) => toApi(f, locale)) });
});

export const POST = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, RecipeBody);
  return json({ food: await saveRecipe(userId, await localeOf(userId), body) }, 201);
});
