import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { toApi } from "@/lib/foods";
import { handler, json } from "@/lib/http";
import { localeOf } from "@/lib/targets";

export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const [rows, locale] = await Promise.all([
    db.favorite.findMany({ where: { userId }, include: { food: true }, orderBy: { createdAt: "desc" } }),
    localeOf(userId),
  ]);
  return json({ foods: rows.map((r) => toApi(r.food, locale)) });
});
