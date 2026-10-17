import type { FoodRow } from "./foods";

/** Countries whose products the import keeps. */
export const OFF_COUNTRIES = ["en:netherlands", "en:denmark", "en:germany"];

export interface OffProduct {
  code?: string;
  product_name?: string;
  product_name_nl?: string;
  product_name_da?: string;
  product_name_de?: string;
  product_name_en?: string;
  brands?: string;
  countries_tags?: string[];
  serving_quantity?: number | string;
  nutriments?: Record<string, number | string | undefined>;
}

function num(v: unknown): number | null {
  const n = typeof v === "string" ? Number(v.replace(",", ".")) : typeof v === "number" ? v : NaN;
  return Number.isFinite(n) && n >= 0 ? n : null;
}

const clean = (s?: string) => (s && s.trim() ? s.trim() : null);

/** Maps an Open Food Facts product to a food row, or null when it lacks a name or energy value. */
export function offToFood(p: OffProduct): FoodRow | null {
  const code = clean(p.code);
  if (!code) return null;
  const n = p.nutriments ?? {};
  const kj = num(n["energy-kj_100g"]) ?? num(n["energy_100g"]);
  const kcal = num(n["energy-kcal_100g"]) ?? (kj !== null ? kj / 4.184 : null);
  // Anything above pure fat (900 kcal/100 g) is a data-entry error.
  if (kcal === null || kcal > 900) return null;
  const generic = clean(p.product_name);
  const row: FoodRow = {
    source: "OFF",
    sourceId: code,
    barcode: code,
    nameNl: clean(p.product_name_nl),
    nameDa: clean(p.product_name_da),
    nameDe: clean(p.product_name_de),
    nameEn: clean(p.product_name_en) ?? generic,
    brand: clean(p.brands?.split(",")[0]),
    kcal: Math.round(kcal * 10) / 10,
    protein: num(n["proteins_100g"]),
    carbs: num(n["carbohydrates_100g"]),
    fat: num(n["fat_100g"]),
    fiber: num(n["fiber_100g"]),
    sugar: num(n["sugars_100g"]),
    salt: num(n["salt_100g"]),
    servingG: num(p.serving_quantity),
  };
  if (!row.nameNl && !row.nameDa && !row.nameDe && !row.nameEn) return null;
  return row;
}

const FIELDS = "code,product_name,product_name_nl,product_name_da,product_name_de,product_name_en,brands,serving_quantity,nutriments";

/** Live lookup for a barcode the import does not have (yet). */
export async function fetchOffProduct(barcode: string): Promise<FoodRow | null> {
  const res = await fetch(`https://world.openfoodfacts.org/api/v2/product/${encodeURIComponent(barcode)}.json?fields=${FIELDS}`, {
    // Open Food Facts asks every client to identify itself.
    headers: { "User-Agent": process.env.OFF_USER_AGENT ?? "WeightLossApp/0.1" },
    signal: AbortSignal.timeout(8000),
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Open Food Facts answered ${res.status}`);
  const body = (await res.json()) as { status?: number; product?: OffProduct };
  if (body.status !== 1 || !body.product) return null;
  return offToFood({ ...body.product, code: body.product.code ?? barcode });
}
