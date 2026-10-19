import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { parseDay } from "@/lib/dates";
import { handler, json, parseBody } from "@/lib/http";
import { portion } from "@/lib/nutrition";

const MealEnum = z.enum(["BREAKFAST", "LUNCH", "DINNER", "SNACK"]);
const Body = z.object({
  from: z.string(),
  to: z.string(),
  // Without a meal the whole day is copied, each entry to its own meal.
  meal: MealEnum.optional(),
});

/** Copies one meal (or a whole day) to another day: "same breakfast as yesterday". */
export const POST = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, Body);
  const source = await db.diaryEntry.findMany({
    where: { userId, date: parseDay(body.from), ...(body.meal ? { meal: body.meal } : {}) },
    include: { food: true },
    orderBy: { createdAt: "asc" },
  });
  const to = parseDay(body.to);
  await db.diaryEntry.createMany({
    data: source.map((e) => ({
      userId,
      date: to,
      meal: e.meal,
      foodId: e.foodId,
      grams: e.grams,
      ...portion(e.food, e.grams),
    })),
  });
  return json({ copied: source.length });
});
