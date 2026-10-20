import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { handler, HttpError, json, parseId } from "@/lib/http";

/** Only whoever added a unit may remove it; anyone else gets 404. */
export const DELETE = handler(async (req: Request, ctx: { params: Promise<{ id: string; unitId: string }> }) => {
  const userId = await requireUser(req);
  const params = await ctx.params;
  const { count } = await db.foodUnit.deleteMany({
    where: { id: parseId(params.unitId), foodId: parseId(params.id), createdById: userId },
  });
  if (count === 0) throw new HttpError(404, "no such unit");
  return json({ ok: true });
});
