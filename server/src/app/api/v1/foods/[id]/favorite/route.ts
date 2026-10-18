import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { handler, HttpError, json, parseId } from "@/lib/http";

type Ctx = { params: Promise<{ id: string }> };

export const POST = handler(async (req: Request, ctx: Ctx) => {
  const userId = await requireUser(req);
  const foodId = parseId((await ctx.params).id);
  if (!(await db.food.findUnique({ where: { id: foodId }, select: { id: true } }))) throw new HttpError(404, "no such food");
  await db.favorite.upsert({ where: { userId_foodId: { userId, foodId } }, create: { userId, foodId }, update: {} });
  return json({ ok: true });
});

export const DELETE = handler(async (req: Request, ctx: Ctx) => {
  const userId = await requireUser(req);
  const foodId = parseId((await ctx.params).id);
  await db.favorite.deleteMany({ where: { userId, foodId } });
  return json({ ok: true });
});
