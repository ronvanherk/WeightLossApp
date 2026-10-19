import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { handler, HttpError, json, parseBody, parseId } from "@/lib/http";
import { RecipeBody, recipeForEdit, recipeOrThrow, saveRecipe } from "@/lib/recipe-store";
import { localeOf } from "@/lib/targets";

type Ctx = { params: Promise<{ id: string }> };

export const GET = handler(async (req: Request, ctx: Ctx) => {
  const userId = await requireUser(req);
  return json(await recipeForEdit(parseId((await ctx.params).id), await localeOf(userId)));
});

/** Either user may edit; diary entries keep the values they were logged with. */
export const PUT = handler(async (req: Request, ctx: Ctx) => {
  const userId = await requireUser(req);
  const id = parseId((await ctx.params).id);
  await recipeOrThrow(id);
  const body = await parseBody(req, RecipeBody);
  return json({ food: await saveRecipe(userId, await localeOf(userId), body, id) });
});

export const DELETE = handler(async (req: Request, ctx: Ctx) => {
  await requireUser(req);
  const id = parseId((await ctx.params).id);
  await recipeOrThrow(id);
  const [logged, usedIn] = await Promise.all([
    db.diaryEntry.count({ where: { foodId: id } }),
    db.foodIngredient.count({ where: { ingredientId: id } }),
  ]);
  if (logged > 0) throw new HttpError(409, "Dit recept staat in het dagboek en kan niet weg.");
  if (usedIn > 0) throw new HttpError(409, "Dit recept wordt in een ander recept gebruikt.");
  await db.food.delete({ where: { id } });
  return json({ ok: true });
});
