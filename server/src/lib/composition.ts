// Shared by the Frida (DK) and BLS (DE) importers. Both use EuroFIR component codes, which are
// what NEVO uses too, so their values land in `food.nutrients` under the codes the app already
// knows. Only codes in the `nutrient` table are kept; units are converted to NEVO's.
import type { FoodRow } from "./foods";

const FACTOR: Record<string, number> = { g: 1, mg: 1e-3, µg: 1e-6, ug: 1e-6, mcg: 1e-6 };

/** "mg/100g", "µg/100 g", "kcal/100 g" -> "mg", "µg", "kcal". */
export function baseUnit(unit: string): string {
  return unit.trim().split("/")[0].trim().replace(/^ug$/, "µg");
}

/** `value` in `from` expressed in `to`; mass units convert, anything else must match. */
export function convert(value: number, from: string, to: string): number | null {
  const a = baseUnit(from);
  const b = baseUnit(to);
  if (a === b) return value;
  if (FACTOR[a] !== undefined && FACTOR[b] !== undefined) return (value * FACTOR[a]) / FACTOR[b];
  return null;
}

/**
 * A cell from a composition table: a number, or a marker. Trace and below-detection-limit
 * count as 0; "-" and empty mean unknown.
 */
export function cellValue(v: unknown): number | null {
  if (typeof v === "number") return Number.isFinite(v) && v >= 0 ? v : null;
  if (typeof v !== "string") return null;
  const s = v.trim();
  if (s === "TR" || s.startsWith("<")) return 0;
  const n = Number(s.replace(",", "."));
  return s !== "" && Number.isFinite(n) && n >= 0 ? n : null;
}

const round = (v: number) => Math.round(v * 1000) / 1000;

/** The fixed columns of a food row from its nutrients by NEVO code; salt from sodium if not given. */
export function mainValues(n: Record<string, number>, salt?: number | null): Pick<
  FoodRow,
  "kcal" | "protein" | "carbs" | "fat" | "saturatedFat" | "fiber" | "sugar" | "salt"
> {
  return {
    kcal: Math.round(n.ENERCC * 10) / 10,
    protein: n.PROT ?? null,
    carbs: n.CHO ?? null,
    fat: n.FAT ?? null,
    saturatedFat: n.FASAT ?? null,
    fiber: n.FIBT ?? null,
    sugar: n.SUGAR ?? null,
    salt: salt ?? (n.NA === undefined ? null : Math.round(n.NA * 2.5) / 1000),
  };
}

export function roundAll(n: Record<string, number>): Record<string, number> {
  return Object.fromEntries(Object.entries(n).map(([k, v]) => [k, round(v)]));
}
