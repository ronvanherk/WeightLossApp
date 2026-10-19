import { requireUser } from "@/lib/auth";
import { searchFoods, toApi } from "@/lib/foods";
import { handler, json } from "@/lib/http";
import { localeOf } from "@/lib/targets";

export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const url = new URL(req.url);
  const q = url.searchParams.get("q") ?? "";
  const limit = Number(url.searchParams.get("limit") ?? 25) || 25;
  const locale = await localeOf(userId);
  const foods = await searchFoods(q, limit, locale);
  return json({ foods: foods.map((f) => toApi(f, locale)) });
});
