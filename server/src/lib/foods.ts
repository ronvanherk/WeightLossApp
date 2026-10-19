import { Prisma, type Food, type FoodSource } from "@prisma/client";
import { db } from "./db";

export const LOCALES = ["nl", "da", "de"] as const;
export type Locale = (typeof LOCALES)[number];

/** A food as importers produce it; upsertFoods() keys on (source, sourceId). */
export interface FoodRow {
  source: FoodSource;
  sourceId: string;
  barcode?: string | null;
  nameNl?: string | null;
  nameDa?: string | null;
  nameDe?: string | null;
  nameEn?: string | null;
  brand?: string | null;
  kcal: number;
  protein?: number | null;
  carbs?: number | null;
  fat?: number | null;
  fiber?: number | null;
  sugar?: number | null;
  salt?: number | null;
  servingG?: number | null;
  saturatedFat?: number | null;
  unit?: "g" | "ml";
  category?: string | null;
  nutrients?: Record<string, number> | null;
  details?: FoodDetails | null;
}

/** Extras a source knows about a food; shown on the detail screen, synonyms also searched. */
export interface FoodDetails {
  synonyms?: string[];
  remark?: string;
  traces?: string;
  fortified?: string;
}

export function searchText(f: Pick<FoodRow, "nameNl" | "nameDa" | "nameDe" | "nameEn" | "brand" | "details">): string {
  return [f.brand, f.nameNl, f.nameDa, f.nameDe, f.nameEn, ...(f.details?.synonyms ?? [])]
    .filter(Boolean)
    .join(" ")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

/** Name in the user's language, falling back through the others. */
export function displayName(f: Pick<Food, "nameNl" | "nameDa" | "nameDe" | "nameEn">, locale: string): string {
  const byLocale: Record<string, string | null> = { nl: f.nameNl, da: f.nameDa, de: f.nameDe, en: f.nameEn };
  return byLocale[locale] ?? f.nameNl ?? f.nameDe ?? f.nameDa ?? f.nameEn ?? "?";
}

export function toApi(f: Food, locale: string) {
  return {
    id: f.id,
    source: f.source,
    barcode: f.barcode,
    name: displayName(f, locale),
    names: { nl: f.nameNl, da: f.nameDa, de: f.nameDe, en: f.nameEn },
    brand: f.brand,
    per100g: {
      kcal: f.kcal,
      protein: f.protein,
      carbs: f.carbs,
      fat: f.fat,
      saturatedFat: f.saturatedFat,
      fiber: f.fiber,
      sugar: f.sugar,
      salt: f.salt,
    },
    servingG: f.servingG,
    unit: f.unit,
    category: f.category,
  };
}

/** Bulk insert-or-update in one statement per batch; fast enough for a full Open Food Facts import. */
export async function upsertFoods(rows: FoodRow[]): Promise<number> {
  if (rows.length === 0) return 0;
  const records = rows.map((r) => ({
    source: r.source,
    source_id: r.sourceId,
    barcode: r.barcode ?? null,
    name_nl: r.nameNl ?? null,
    name_da: r.nameDa ?? null,
    name_de: r.nameDe ?? null,
    name_en: r.nameEn ?? null,
    brand: r.brand ?? null,
    kcal: r.kcal,
    protein: r.protein ?? null,
    carbs: r.carbs ?? null,
    fat: r.fat ?? null,
    fiber: r.fiber ?? null,
    sugar: r.sugar ?? null,
    salt: r.salt ?? null,
    serving_g: r.servingG ?? null,
    saturated_fat: r.saturatedFat ?? null,
    unit: r.unit ?? "g",
    category: r.category ?? null,
    nutrients: r.nutrients ?? null,
    details: r.details ?? null,
    search_text: searchText(r),
  }));
  return db.$executeRaw`
    INSERT INTO food (source, source_id, barcode, name_nl, name_da, name_de, name_en, brand,
                      kcal, protein, carbs, fat, fiber, sugar, salt, serving_g, saturated_fat, unit,
                      category, nutrients, details, search_text, updated_at)
    SELECT source::"FoodSource", source_id, barcode, name_nl, name_da, name_de, name_en, brand,
           kcal, protein, carbs, fat, fiber, sugar, salt, serving_g, saturated_fat, unit,
           category, nutrients, details, search_text, now()
    FROM jsonb_to_recordset(${JSON.stringify(records)}::jsonb) AS r(
      source text, source_id text, barcode text, name_nl text, name_da text, name_de text, name_en text,
      brand text, kcal float8, protein float8, carbs float8, fat float8, fiber float8, sugar float8,
      salt float8, serving_g float8, saturated_fat float8, unit text, category text, nutrients jsonb,
      details jsonb, search_text text)
    ON CONFLICT (source, source_id) DO UPDATE SET
      barcode = EXCLUDED.barcode, name_nl = EXCLUDED.name_nl, name_da = EXCLUDED.name_da,
      name_de = EXCLUDED.name_de, name_en = EXCLUDED.name_en, brand = EXCLUDED.brand,
      kcal = EXCLUDED.kcal, protein = EXCLUDED.protein, carbs = EXCLUDED.carbs, fat = EXCLUDED.fat,
      fiber = EXCLUDED.fiber, sugar = EXCLUDED.sugar, salt = EXCLUDED.salt,
      serving_g = EXCLUDED.serving_g, saturated_fat = EXCLUDED.saturated_fat, unit = EXCLUDED.unit,
      category = EXCLUDED.category, nutrients = EXCLUDED.nutrients, details = EXCLUDED.details,
      search_text = EXCLUDED.search_text, updated_at = now()`;
}

/** The name column per user language; fixed strings, safe to put into SQL. */
const NAME_COLUMN: Record<string, string> = { nl: "name_nl", da: "name_da", de: "name_de" };

/**
 * Every word must occur (ILIKE, served by the trigram index). Foods where every word occurs in
 * the user's own language (name, brand or synonyms) come first, so a Dutch "kaas" is not buried
 * under a Danish or German name that happens to match; then best match, official tables before
 * branded products on a tie.
 */
export async function searchFoods(query: string, limit = 25, locale = "nl"): Promise<Food[]> {
  const q = query.toLowerCase().replace(/\s+/g, " ").trim();
  const words = q.split(" ").filter((w) => w.length >= 2);
  if (words.length === 0) return [];
  const patterns = words.map((w) => `%${w.replace(/[\\%_]/g, (c) => `\\${c}`)}%`);
  const own = Prisma.raw(NAME_COLUMN[locale] ?? "name_nl");
  const ids = await db.$queryRaw<{ id: number }[]>`
    SELECT id FROM food
    WHERE search_text ILIKE ALL (${patterns}::text[])
    ORDER BY concat_ws(' ', brand, ${own}, details->>'synonyms') ILIKE ALL (${patterns}::text[]) DESC,
             word_similarity(${q}, search_text) DESC,
             CASE WHEN source IN ('NEVO', 'FRIDA', 'BLS') THEN 0 WHEN source IN ('CUSTOM', 'RECIPE') THEN 1 ELSE 2 END,
             length(search_text)
    LIMIT ${Prisma.raw(String(Math.min(Math.max(limit, 1), 100)))}`;
  const foods = await db.food.findMany({ where: { id: { in: ids.map((r) => r.id) } } });
  const byId = new Map(foods.map((f) => [f.id, f]));
  return ids.map((r) => byId.get(r.id)!).filter(Boolean);
}
