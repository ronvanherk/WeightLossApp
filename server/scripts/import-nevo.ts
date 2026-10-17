// Usage: pnpm import:nevo <NEVO csv>
// The NEVO-online export (https://nevo-online.rivm.nl) is a delimited file with one row per
// food and Dutch/English column headers. Columns are found by header, not by position, so a
// new NEVO release with extra nutrients still imports; a missing one stops the import.
import { readFileSync } from "node:fs";
import { upsertFoods, type FoodRow } from "../src/lib/foods";
import { db } from "../src/lib/db";

const COLUMNS = {
  code: /^NEVO-?code/i,
  nameNl: /^Voedingsmiddelnaam/i,
  nameEn: /^Engelse naam|^Food name/i,
  kcal: /^ENERCC\b/i,
  protein: /^PROT\b(?!PL|AN)/i,
  carbs: /^CHO\b/i,
  fat: /^FAT\b/i,
  fiber: /^FIBT\b/i,
  sugar: /^SUGAR\b/i,
  sodiumMg: /^NA\b/i,
} as const;

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

const num = (s: string | undefined) => {
  if (!s) return null;
  const n = Number(s.replace(",", "."));
  return Number.isFinite(n) ? n : null;
};

async function main() {
  const file = process.argv[2];
  if (!file) throw new Error("usage: pnpm import:nevo <file>");
  // NEVO exports have shipped as UTF-8 and as Latin-1; a replacement char means the latter.
  let text = readFileSync(file, "utf8");
  if (text.includes("�")) text = readFileSync(file, "latin1");
  const lines = text.split(/\r?\n/).filter((l) => l.trim());
  const sep = ["|", ";", "\t", ","].find((s) => lines[0].includes(s))!;
  const header = parseLine(lines[0], sep);

  const idx = {} as Record<keyof typeof COLUMNS, number>;
  for (const [key, re] of Object.entries(COLUMNS) as [keyof typeof COLUMNS, RegExp][]) {
    idx[key] = header.findIndex((h) => re.test(h));
    if (idx[key] < 0) throw new Error(`column for ${key} not found; headers are:\n${header.join("\n")}`);
  }

  const rows: FoodRow[] = [];
  for (const line of lines.slice(1)) {
    const c = parseLine(line, sep);
    const kcal = num(c[idx.kcal]);
    if (!c[idx.code] || kcal === null) continue;
    const sodium = num(c[idx.sodiumMg]);
    rows.push({
      source: "NEVO",
      sourceId: c[idx.code],
      nameNl: c[idx.nameNl] || null,
      nameEn: c[idx.nameEn] || null,
      kcal,
      protein: num(c[idx.protein]),
      carbs: num(c[idx.carbs]),
      fat: num(c[idx.fat]),
      fiber: num(c[idx.fiber]),
      sugar: num(c[idx.sugar]),
      // Salt = sodium x 2.5.
      salt: sodium === null ? null : Math.round(sodium * 2.5) / 1000,
    });
  }
  let done = 0;
  for (let i = 0; i < rows.length; i += 1000) done += await upsertFoods(rows.slice(i, i + 1000));
  console.log(`NEVO: ${done} foods imported from ${lines.length - 1} rows`);
}

main().finally(() => db.$disconnect());
