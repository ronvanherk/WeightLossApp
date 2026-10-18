import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { toApi } from "@/lib/foods";
import { handler, json } from "@/lib/http";
import { localeOf } from "@/lib/targets";

/**
 * Foods this user logged lately, most recent first, each with the grams used last time.
 * With `meal`, foods logged at that meal come first: breakfast foods for breakfast.
 */
export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const meal = new URL(req.url).searchParams.get("meal");
  const rows = await db.$queryRaw<{ food_id: number; grams: number }[]>`
    SELECT food_id, (array_agg(grams ORDER BY created_at DESC))[1] AS grams
    FROM diary_entry
    WHERE user_id = ${userId}
    GROUP BY food_id
    ORDER BY bool_or(meal::text = ${meal ?? ""}) DESC, max(created_at) DESC
    LIMIT 40`;
  const [foods, locale] = await Promise.all([
    db.food.findMany({ where: { id: { in: rows.map((r) => r.food_id) } } }),
    localeOf(userId),
  ]);
  const byId = new Map(foods.map((f) => [f.id, f]));
  return json({
    foods: rows
      .filter((r) => byId.has(r.food_id))
      .map((r) => ({ ...toApi(byId.get(r.food_id)!, locale), lastGrams: r.grams })),
  });
});
