// Usage: pnpm import:bls <BLS_4_0_Daten_2025_DE.xlsx>
// Bundeslebensmittelschlüssel 4.0, Max Rubner-Institut, CC BY 4.0, free since December 2025.
// Download: https://www.blsdb.de/assets/uploads/BLS_4_0_2025_DE.zip (the "Daten" workbook).
// One sheet: "BLS Code | Lebensmittelbezeichnung | Food name", then per nutrient three columns:
// "<CODE> <name> [<unit>/100g]", "<CODE> Datenherkunft", "<CODE> Referenz".
// Values are numbers or markers ("-" unknown, "TR" trace, "<LOD"/"<LOQ"). Safe to re-run.
import { db } from "../src/lib/db";
import { cellValue, convert, mainValues, roundAll } from "../src/lib/composition";
import { upsertFoods, type FoodRow } from "../src/lib/foods";
import { readSheets, sheetWith } from "./xlsx";

/** BLS codes whose NEVO counterpart has another name. */
const ALIAS: Record<string, string> = { PROT625: "PROT" };

/** The first letter of a BLS code is its main group. */
const GROUP: Record<string, string> = {
  B: "Bread and rolls",
  C: "Cereals and flour",
  D: "Biscuits, cakes and snacks",
  E: "Pasta",
  F: "Fruit",
  G: "Vegetables",
  H: "Legumes, nuts and seeds",
  K: "Potatoes and starches",
  M: "Milk and cheese",
  N: "Non-alcoholic drinks",
  P: "Alcoholic drinks",
  Q: "Fats and oils",
  R: "Spices, salt and condiments",
  S: "Sugar and sweets",
  T: "Fish",
  U: "Meat",
  V: "Game, poultry and offal",
  W: "Sausages and cold cuts",
  X: "Composite dishes and soups",
  Y: "Composite dishes",
};

async function main() {
  const file = process.argv[2];
  if (!file) throw new Error("usage: pnpm import:bls <BLS_4_0_Daten_xxxx_DE.xlsx>");
  const rows = sheetWith(await readSheets(file), "BLS Code", "Lebensmittelbezeichnung", "Food name");
  const header = rows[0].map((h) => String(h ?? ""));
  const nevoUnits = new Map((await db.nutrient.findMany()).map((n) => [n.code, n.unit]));

  const columns: { i: number; code: string; unit: string }[] = [];
  let saltCol = -1;
  header.forEach((h, i) => {
    const m = /^(\S+) .*\[(.+?)\/100\s*g\]$/.exec(h);
    if (!m) return;
    if (m[1] === "NACL") saltCol = i;
    const code = ALIAS[m[1]] ?? m[1];
    if (nevoUnits.has(code)) columns.push({ i, code, unit: m[2] });
  });
  if (!columns.some((c) => c.code === "ENERCC")) throw new Error("no ENERCC column");

  const foods: FoodRow[] = [];
  for (const r of rows.slice(1)) {
    const code = r[0] ? String(r[0]).trim() : "";
    if (!code) continue;
    const raw: Record<string, number> = {};
    for (const c of columns) {
      const v = cellValue(r[c.i]);
      const converted = v === null ? null : convert(v, c.unit, nevoUnits.get(c.code)!);
      if (converted !== null) raw[c.code] = converted;
    }
    if (raw.ENERCC === undefined) continue;
    const nutrients = roundAll(raw);
    foods.push({
      source: "BLS",
      sourceId: code,
      nameDe: r[1] ? String(r[1]) : null,
      nameEn: r[2] ? String(r[2]) : null,
      ...mainValues(nutrients, saltCol >= 0 ? cellValue(r[saltCol]) : null),
      category: GROUP[code[0]] ?? null,
      nutrients,
    });
  }
  let done = 0;
  for (let i = 0; i < foods.length; i += 500) done += await upsertFoods(foods.slice(i, i + 500));
  console.log(`BLS: ${done} foods, ${columns.length} nutrients mapped to NEVO codes`);
}

main()
  .catch((e: unknown) => {
    console.error(e);
    process.exitCode = 1;
  })
  .finally(() => db.$disconnect());
