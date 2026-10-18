// Usage: pnpm import:nevo <NEVO folder or NEVO20xx_vX.csv>
// The NEVO-online download (https://www.rivm.nl/nederlands-voedingsstoffenbestand) is a folder of
// pipe-separated files. Three are used:
//   NEVO20xx_vX.csv                 one row per food, every nutrient as a column "CODE (unit)"
//   NEVO20xx_vX_Nutrienten_Nutrients.csv   names, groups and units of those nutrient codes
//   NEVO20xx_vX_Recepten_Recipes.csv       what composite foods are made of
// Columns are found by header, not by position, so a release with extra nutrients still imports.
// Safe to re-run: foods are upserted by NEVO code, nutrients by code, recipes replaced.
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { Prisma } from "@prisma/client";
import { db } from "../src/lib/db";
import { upsertFoods, type FoodRow } from "../src/lib/foods";

function parseLine(line: string, sep: string): string[] {
  // NEVO quotes fields that contain the separator.
  const out: string[] = [];
  let cur = "";
  let quoted = false;
  for (const ch of line) {
    if (ch === '"') quoted = !quoted;
    else if (ch === sep && !quoted) {
      out.push(cur);
      cur = "";
    } else cur += ch;
  }
  out.push(cur);
  return out.map((s) => s.trim());
}

/** Header + rows as objects keyed by header. */
function readTable(file: string): { header: string[]; rows: string[][] } {
  // NEVO exports have shipped as UTF-8 and as Latin-1; a replacement char means the latter.
  let text = readFileSync(file, "utf8");
  if (text.includes("�")) text = readFileSync(file, "latin1");
  const lines = text.replace(/^﻿/, "").split(/\r?\n/).filter((l) => l.trim());
  const sep = ["|", ";", "\t"].find((s) => lines[0].includes(s)) ?? ",";
  return { header: parseLine(lines[0], sep), rows: lines.slice(1).map((l) => parseLine(l, sep)) };
}

const num = (s: string | undefined) => {
  if (!s) return null;
  const n = Number(s.replace(",", "."));
  return Number.isFinite(n) ? n : null;
};

function column(header: string[], re: RegExp, what: string): number {
  const i = header.findIndex((h) => re.test(h));
  if (i < 0) throw new Error(`column for ${what} not found; headers are:\n${header.join("\n")}`);
  return i;
}

/** "ENERCC (kcal)" -> "ENERCC"; non-nutrient columns -> null. */
const nutrientCode = (h: string) => /^([A-Z][A-Z0-9_:]*) \(.+\)$/.exec(h)?.[1] ?? null;

function locate(arg: string) {
  const dir = statSync(arg).isDirectory() ? arg : join(arg, "..");
  const files = readdirSync(dir).filter((f) => f.toLowerCase().endsWith(".csv"));
  const pick = (re: RegExp) => files.find((f) => re.test(f));
  const main = statSync(arg).isDirectory() ? pick(/^NEVO\d{4}_v[\d.]+\.csv$/i) : arg.split("/").pop();
  if (!main) throw new Error(`no NEVO20xx_vX.csv in ${dir}`);
  const opt = (name?: string) => (name && existsSync(join(dir, name)) ? join(dir, name) : null);
  return { main: join(dir, main), nutrients: opt(pick(/Nutrient/i)), recipes: opt(pick(/Recept|Recipe/i)) };
}

async function importFoods(file: string): Promise<number> {
  const { header, rows } = readTable(file);
  const c = {
    group: column(header, /^Voedingsmiddelgroep/i, "food group"),
    code: column(header, /^NEVO-?code/i, "code"),
    nameNl: column(header, /^Voedingsmiddelnaam/i, "Dutch name"),
    nameEn: column(header, /^Engelse naam|^Food name/i, "English name"),
    synonym: header.findIndex((h) => /^Synoniem/i.test(h)),
    quantity: header.findIndex((h) => /^Hoeveelheid|Quantity/i.test(h)),
    remark: header.findIndex((h) => /^Opmerking/i.test(h)),
    traces: header.findIndex((h) => /sporen|traces/i.test(h)),
    fortified: header.findIndex((h) => /verrijkt|fortified/i.test(h)),
  };
  const nutrientCols = header.map((h, i) => [nutrientCode(h), i] as const).filter(([code]) => code !== null);
  for (const needed of ["ENERCC", "PROT", "CHO", "FAT"]) {
    if (!nutrientCols.some(([code]) => code === needed)) throw new Error(`nutrient column ${needed} not found`);
  }

  const foods: FoodRow[] = [];
  for (const r of rows) {
    if (!r[c.code]) continue;
    const nutrients: Record<string, number> = {};
    for (const [code, i] of nutrientCols) {
      const v = num(r[i]);
      if (v !== null) nutrients[code!] = v;
    }
    if (nutrients.ENERCC === undefined) continue;
    const text = (i: number) => (i >= 0 && r[i] ? r[i] : undefined);
    const details = {
      synonyms: text(c.synonym) ? [text(c.synonym)!] : undefined,
      remark: text(c.remark),
      traces: text(c.traces),
      fortified: text(c.fortified),
    };
    foods.push({
      source: "NEVO",
      sourceId: r[c.code],
      nameNl: r[c.nameNl] || null,
      nameEn: r[c.nameEn] || null,
      kcal: nutrients.ENERCC,
      protein: nutrients.PROT ?? null,
      carbs: nutrients.CHO ?? null,
      fat: nutrients.FAT ?? null,
      saturatedFat: nutrients.FASAT ?? null,
      fiber: nutrients.FIBT ?? null,
      sugar: nutrients.SUGAR ?? null,
      // Salt = sodium x 2.5; NEVO gives sodium in mg.
      salt: nutrients.NA === undefined ? null : Math.round(nutrients.NA * 2.5) / 1000,
      unit: /100\s*ml/i.test(text(c.quantity) ?? "") ? "ml" : "g",
      category: r[c.group] || null,
      nutrients,
      details: Object.values(details).some(Boolean) ? JSON.parse(JSON.stringify(details)) : null,
    });
  }
  let done = 0;
  for (let i = 0; i < foods.length; i += 500) done += await upsertFoods(foods.slice(i, i + 500));
  console.log(`NEVO: ${done} foods from ${rows.length} rows, ${nutrientCols.length} nutrients per food`);
  return done;
}

async function importNutrients(file: string) {
  const { header, rows } = readTable(file);
  const c = {
    groupNl: column(header, /^Voedingsstofgroep/i, "group"),
    groupEn: column(header, /^Component group/i, "component group"),
    code: column(header, /^Nutrient-?code/i, "code"),
    nameNl: column(header, /^Voedingsstof$/i, "name"),
    nameEn: column(header, /^Component$/i, "component"),
    unit: column(header, /^Eenheid|Unit/i, "unit"),
  };
  // The file is a tree: totals repeat as the head of their sub-group (PROT, CHO, FAT ...).
  // The first occurrence keeps its place in the order.
  const seen = new Set<string>();
  let n = 0;
  for (const [i, r] of rows.entries()) {
    if (!r[c.code] || seen.has(r[c.code])) continue;
    seen.add(r[c.code]);
    const data = {
      groupNl: r[c.groupNl],
      groupEn: r[c.groupEn],
      nameNl: r[c.nameNl],
      nameEn: r[c.nameEn],
      unit: r[c.unit],
      sortOrder: i,
    };
    await db.nutrient.upsert({ where: { code: r[c.code] }, create: { code: r[c.code], ...data }, update: data });
    n++;
  }
  console.log(`NEVO: ${n} nutrient definitions`);
}

async function importRecipes(file: string) {
  const { header, rows } = readTable(file);
  const c = {
    code: column(header, /^NEVO-?code/i, "recipe code"),
    ingredient: column(header, /^Ingr_NEVO-?code/i, "ingredient code"),
    share: column(header, /relatieve|relative/i, "relative amount"),
  };
  const ids = new Map(
    (await db.food.findMany({ where: { source: "NEVO" }, select: { id: true, sourceId: true } })).map((f) => [f.sourceId, f.id]),
  );
  const links = new Map<string, { foodId: number; ingredientId: number; share: number }>();
  let missing = 0;
  for (const r of rows) {
    const foodId = ids.get(r[c.code]);
    const ingredientId = ids.get(r[c.ingredient]);
    const share = num(r[c.share]);
    if (!foodId || !ingredientId || share === null) {
      missing++;
      continue;
    }
    // An ingredient listed twice in one recipe is added up.
    const key = `${foodId}:${ingredientId}`;
    const prev = links.get(key);
    links.set(key, { foodId, ingredientId, share: (prev?.share ?? 0) + share });
  }
  const recipeIds = [...new Set([...links.values()].map((l) => l.foodId))];
  await db.$transaction([
    db.foodIngredient.deleteMany({ where: { foodId: { in: recipeIds } } }),
    db.foodIngredient.createMany({ data: [...links.values()] }),
  ]);
  console.log(`NEVO: ${recipeIds.length} recipes, ${links.size} ingredient lines${missing ? `, ${missing} skipped` : ""}`);
}

async function main() {
  const arg = process.argv[2];
  if (!arg) throw new Error("usage: pnpm import:nevo <NEVO folder or csv>");
  const files = locate(arg);
  if (files.nutrients) await importNutrients(files.nutrients);
  await importFoods(files.main);
  if (files.recipes) await importRecipes(files.recipes);
}

main()
  .catch((e: unknown) => {
    console.error(e instanceof Prisma.PrismaClientKnownRequestError ? `${e.code}: ${e.message}` : e);
    process.exitCode = 1;
  })
  .finally(() => db.$disconnect());
