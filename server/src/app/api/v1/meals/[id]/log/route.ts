import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { parseDay } from "@/lib/dates";
import { entriesFromItems, MealEnum, SavedItem } from "@/lib/diary";
import { handler, HttpError, json, parseBody, parseId } from "@/lib/http";

type Ctx = { params: Promise<{ id: string }> };

const Body = z.object({
  date: z.string(),
  meal: MealEnum,
  factor: z.number().min(0.1).max(3).default(1),
});

/** Adds every item of a saved meal to a meal of a day. */
export const POST = handler(async (req: Request, ctx: Ctx) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, Body);
  const saved = await db.savedMeal.findUnique({ where: { id: parseId((await ctx.params).id) } });
  if (!saved || saved.userId !== userId) throw new HttpError(404, "no such meal");
  const items = z.array(SavedItem).catch([]).parse(saved.items);
  const data = await entriesFromItems(userId, parseDay(body.date), body.meal, items, body.factor);
  await db.diaryEntry.createMany({ data });
  return json({ added: data.length }, 201);
});
