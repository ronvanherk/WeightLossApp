// Usage: pnpm import:frida <FCDB_x.y_Dataset.xlsx>
// The Danish Food Composition Database (Frida), DTU Fødevareinstituttet, CC BY 4.0.
// Download: https://doi.org/10.11583/DTU.32312844 (the .xlsx). Sheets used:
//   Food             FoodID, Danish and English name, food group
//   Parameter        ParameterID -> EuroFIR component code and unit
//   Data_Normalised  one row per food and parameter
// Safe to re-run: foods are upserted by FoodID.
import { db } from "../src/lib/db";
import { cellValue, convert, mainValues, roundAll } from "../src/lib/composition";
import { upsertFoods, type FoodRow } from "../src/lib/foods";
import { readSheets, sheetWith, type Cell } from "./xlsx";

/** Energy is one EuroFIR code (ENERC) in two units; NEVO splits it into two codes. */
const ENERGY: Record<number, string> = { 356: "ENERCC", 137: "ENERCJ" };
/** Alternative definitions of a nutrient that also has a main parameter: labelling values etc. */
const SKIP = new Set([316, 359, 317, 318, 421, 418, 202]);
const SALT = 327;

const col = (rows: Cell[][], name: string) => {
  const i = rows[0].indexOf(name);
  if (i < 0) throw new Error(`column ${name} not found`);
  return i;
};

async function main() {
  const file = process.argv[2];
  if (!file) throw new Error("usage: pnpm import:frida <FCDB_Dataset.xlsx>");
  const sheets = await readSheets(file);
  const foodSheet = sheetWith(sheets, "FoodID", "FødevareNavn", "FoodGroup");
  const paramSheet = sheetWith(sheets, "ParameterID", "EurofirComponentID");
  const data = sheetWith(sheets, "FoodID", "ParameterID", "ResVal");

  const nevoUnits = new Map((await db.nutrient.findMany()).map((n) => [n.code, n.unit]));
  const p = { id: col(paramSheet, "ParameterID"), code: col(paramSheet, "EurofirComponentID"), unit: col(paramSheet, "EurofirUnitID") };
  const params = new Map<number, { code: string; unit: string }>();
  for (const r of paramSheet.slice(1)) {
    const id = Number(r[p.id]);
    if (SKIP.has(id)) continue;
    const code = ENERGY[id] ?? String(r[p.code] ?? "");
    if (nevoUnits.has(code)) params.set(id, { code, unit: String(r[p.unit] ?? "") });
  }

  const d = { food: col(data, "FoodID"), param: col(data, "ParameterID"), value: col(data, "ResVal") };
  const values = new Map<number, { nutrients: Record<string, number>; salt: number | null }>();
  let unconverted = 0;
  for (const r of data.slice(1)) {
    const foodId = Number(r[d.food]);
    const paramId = Number(r[d.param]);
    const v = cellValue(r[d.value]);
    if (v === null) continue;
    const entry = values.get(foodId) ?? { nutrients: {}, salt: null };
    values.set(foodId, entry);
    if (paramId === SALT) entry.salt = v;
    const param = params.get(paramId);
    // The first parameter with a value wins when two map to one code (thiamin is listed twice).
    if (!param || entry.nutrients[param.code] !== undefined) continue;
    const converted = convert(v, param.unit, nevoUnits.get(param.code)!);
    if (converted === null) unconverted++;
    else entry.nutrients[param.code] = converted;
  }

  const f = { id: col(foodSheet, "FoodID"), da: col(foodSheet, "FødevareNavn"), en: col(foodSheet, "FoodName"), group: col(foodSheet, "FoodGroup") };
  const foods: FoodRow[] = [];
  for (const r of foodSheet.slice(1)) {
    const id = Number(r[f.id]);
    const v = values.get(id);
    if (!id || !v || v.nutrients.ENERCC === undefined) continue;
    const nutrients = roundAll(v.nutrients);
    foods.push({
      source: "FRIDA",
      sourceId: String(id),
      nameDa: r[f.da] ? String(r[f.da]) : null,
      nameEn: r[f.en] ? String(r[f.en]) : null,
      ...mainValues(nutrients, v.salt),
      category: r[f.group] ? String(r[f.group]) : null,
      nutrients,
    });
  }
  let done = 0;
  for (let i = 0; i < foods.length; i += 500) done += await upsertFoods(foods.slice(i, i + 500));
  console.log(`Frida: ${done} foods, ${params.size} parameters mapped to NEVO codes${unconverted ? `, ${unconverted} values in other units skipped` : ""}`);
}

main()
  .catch((e: unknown) => {
    console.error(e);
    process.exitCode = 1;
  })
  .finally(() => db.$disconnect());
