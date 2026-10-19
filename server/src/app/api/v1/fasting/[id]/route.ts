import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { fastToApi, Goal } from "@/lib/fasting";
import { handler, HttpError, json, parseBody, parseId } from "@/lib/http";

type Ctx = { params: Promise<{ id: string }> };

async function ownFast(userId: number, ctx: Ctx) {
  const fast = await db.fast.findUnique({ where: { id: parseId((await ctx.params).id) } });
  if (!fast || fast.userId !== userId) throw new HttpError(404, "no such fast");
  return fast;
}

const Patch = z.object({
  goalHours: Goal.optional(),
  startedAt: z.iso.datetime({ offset: true }).optional(),
  // true = end now.
  end: z.boolean().optional(),
});

export const PATCH = handler(async (req: Request, ctx: Ctx) => {
  const userId = await requireUser(req);
  const fast = await ownFast(userId, ctx);
  const body = await parseBody(req, Patch);
  const startedAt = body.startedAt ? new Date(body.startedAt) : fast.startedAt;
  const endedAt = body.end ? new Date() : fast.endedAt;
  if (startedAt.getTime() > (endedAt ?? new Date()).getTime() + 60_000) throw new HttpError(400, "Het begin ligt na het einde.");
  const updated = await db.fast.update({
    where: { id: fast.id },
    data: { startedAt, endedAt, goalHours: body.goalHours ?? fast.goalHours },
  });
  return json({ fast: fastToApi(updated) });
});

export const DELETE = handler(async (req: Request, ctx: Ctx) => {
  const userId = await requireUser(req);
  const fast = await ownFast(userId, ctx);
  await db.fast.delete({ where: { id: fast.id } });
  return json({ ok: true });
});
