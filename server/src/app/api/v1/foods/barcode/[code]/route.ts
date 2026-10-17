import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { toApi, upsertFoods } from "@/lib/foods";
import { handler, HttpError, json } from "@/lib/http";
import { fetchOffProduct } from "@/lib/openfoodfacts";
import { localeOf } from "@/lib/targets";

export const GET = handler(async (req: Request, ctx: { params: Promise<{ code: string }> }) => {
  const userId = await requireUser(req);
  const { code } = await ctx.params;
  if (!/^\d{6,14}$/.test(code)) throw new HttpError(400, "barcode must be 6-14 digits");
  const locale = await localeOf(userId);

  // A product someone entered by hand wins over the import.
  const known = await db.food.findFirst({ where: { barcode: code }, orderBy: [{ source: "desc" }, { updatedAt: "desc" }] });
  if (known) return json({ food: toApi(known, locale) });

  let row;
  try {
    row = await fetchOffProduct(code);
  } catch {
    throw new HttpError(502, "Open Food Facts is unreachable; try again or enter the product yourself");
  }
  if (!row) throw new HttpError(404, "unknown barcode");
  await upsertFoods([row]);
  const food = await db.food.findUniqueOrThrow({ where: { source_sourceId: { source: "OFF", sourceId: row.sourceId } } });
  return json({ food: toApi(food, locale) });
});
