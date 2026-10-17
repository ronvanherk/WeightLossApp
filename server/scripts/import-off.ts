// Usage: pnpm import:off <openfoodfacts-products.jsonl.gz>
// Download: https://static.openfoodfacts.org/data/openfoodfacts-products.jsonl.gz (~7 GB).
// Streams the dump and keeps the products sold in NL, DK or DE. Safe to re-run: rows are
// updated in place by barcode, so diary entries keep pointing at them.
import { createReadStream } from "node:fs";
import { PassThrough } from "node:stream";
import { createInterface } from "node:readline";
import { createGunzip } from "node:zlib";
import { db } from "../src/lib/db";
import { upsertFoods, type FoodRow } from "../src/lib/foods";
import { OFF_COUNTRIES, offToFood, type OffProduct } from "../src/lib/openfoodfacts";

async function main() {
  const file = process.argv[2];
  if (!file) throw new Error("usage: pnpm import:off <dump.jsonl.gz>");
  const input = createReadStream(file).pipe(file.endsWith(".gz") ? createGunzip() : new PassThrough());
  const lines = createInterface({ input, crlfDelay: Infinity });

  let seen = 0;
  let kept = 0;
  let batch: FoodRow[] = [];
  for await (const line of lines) {
    seen++;
    // Cheap pre-filter before JSON.parse: most of the world's products are skipped here.
    if (!OFF_COUNTRIES.some((c) => line.includes(c))) continue;
    let p: OffProduct;
    try {
      p = JSON.parse(line);
    } catch {
      continue;
    }
    if (!p.countries_tags?.some((c) => OFF_COUNTRIES.includes(c))) continue;
    const row = offToFood(p);
    if (!row) continue;
    batch.push(row);
    if (batch.length === 1000) {
      kept += await upsertFoods(batch);
      batch = [];
      if (kept % 20000 === 0) console.log(`${kept} kept of ${seen} read`);
    }
  }
  kept += await upsertFoods(batch);
  console.log(`Open Food Facts: ${kept} products kept of ${seen} read`);
}

main().finally(() => db.$disconnect());
