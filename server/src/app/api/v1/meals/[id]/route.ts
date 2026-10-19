import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { handler, HttpError, json, parseId } from "@/lib/http";

type Ctx = { params: Promise<{ id: string }> };

export const DELETE = handler(async (req: Request, ctx: Ctx) => {
  const userId = await requireUser(req);
  const { count } = await db.savedMeal.deleteMany({ where: { id: parseId((await ctx.params).id), userId } });
  if (count === 0) throw new HttpError(404, "no such meal");
  return json({ ok: true });
});
