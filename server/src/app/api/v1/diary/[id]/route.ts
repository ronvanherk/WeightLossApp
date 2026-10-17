import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { handler, HttpError, json, parseBody, parseId } from "@/lib/http";
import { portion } from "@/lib/nutrition";

type Ctx = { params: Promise<{ id: string }> };

async function ownEntry(userId: number, ctx: Ctx) {
  const entry = await db.diaryEntry.findUnique({ where: { id: parseId((await ctx.params).id) }, include: { food: true } });
  // Someone else's entry is reported as missing, not forbidden.
  if (!entry || entry.userId !== userId) throw new HttpError(404, "no such entry");
  return entry;
}

const Patch = z.object({
  grams: z.number().positive().max(5000).optional(),
  meal: z.enum(["BREAKFAST", "LUNCH", "DINNER", "SNACK"]).optional(),
});

export const PATCH = handler(async (req: Request, ctx: Ctx) => {
  const userId = await requireUser(req);
  const entry = await ownEntry(userId, ctx);
  const body = await parseBody(req, Patch);
  const grams = body.grams ?? entry.grams;
  await db.diaryEntry.update({
    where: { id: entry.id },
    // Recomputed from the food as it is now; the snapshot only protects untouched entries.
    data: { meal: body.meal ?? entry.meal, grams, ...portion(entry.food, grams) },
  });
  return json({ ok: true });
});

export const DELETE = handler(async (req: Request, ctx: Ctx) => {
  const userId = await requireUser(req);
  const entry = await ownEntry(userId, ctx);
  await db.diaryEntry.delete({ where: { id: entry.id } });
  return json({ ok: true });
});
