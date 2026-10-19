import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { foodValues, MealEnum, QuickValues, quickValues } from "@/lib/diary";
import { handler, HttpError, json, parseBody, parseId } from "@/lib/http";

type Ctx = { params: Promise<{ id: string }> };

async function ownEntry(userId: number, ctx: Ctx) {
  const entry = await db.diaryEntry.findUnique({ where: { id: parseId((await ctx.params).id) }, include: { food: true } });
  // Someone else's entry is reported as missing, not forbidden.
  if (!entry || entry.userId !== userId) throw new HttpError(404, "no such entry");
  return entry;
}

// grams for a food entry; name and values for a quick entry.
const Patch = QuickValues.partial().extend({
  grams: z.number().positive().max(5000).optional(),
  meal: MealEnum.optional(),
});

export const PATCH = handler(async (req: Request, ctx: Ctx) => {
  const userId = await requireUser(req);
  const entry = await ownEntry(userId, ctx);
  const body = await parseBody(req, Patch);
  const meal = body.meal ?? entry.meal;
  if (entry.food) {
    // Recomputed from the food as it is now; the snapshot only protects untouched entries.
    await db.diaryEntry.update({ where: { id: entry.id }, data: { meal, ...foodValues(entry.food, body.grams ?? entry.grams) } });
  } else {
    const values = quickValues({
      name: body.name ?? entry.name ?? "?",
      kcal: body.kcal ?? entry.kcal,
      protein: body.protein ?? entry.protein,
      carbs: body.carbs ?? entry.carbs,
      fat: body.fat ?? entry.fat,
    });
    await db.diaryEntry.update({ where: { id: entry.id }, data: { meal, ...values } });
  }
  return json({ ok: true });
});

export const DELETE = handler(async (req: Request, ctx: Ctx) => {
  const userId = await requireUser(req);
  const entry = await ownEntry(userId, ctx);
  await db.diaryEntry.delete({ where: { id: entry.id } });
  return json({ ok: true });
});
