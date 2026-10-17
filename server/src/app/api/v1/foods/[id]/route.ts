import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { toApi } from "@/lib/foods";
import { handler, HttpError, json, parseId } from "@/lib/http";
import { localeOf } from "@/lib/targets";

export const GET = handler(async (req: Request, ctx: { params: Promise<{ id: string }> }) => {
  const userId = await requireUser(req);
  const food = await db.food.findUnique({ where: { id: parseId((await ctx.params).id) } });
  if (!food) throw new HttpError(404, "no such food");
  return json({ food: toApi(food, await localeOf(userId)) });
});
