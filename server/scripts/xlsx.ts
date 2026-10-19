// Streams every worksheet of an .xlsx into arrays of plain cell values. Sheets are told apart
// by their header row, not their name: ExcelJS's streaming reader does not know names up front.
import ExcelJS from "exceljs";

export type Cell = string | number | null;

function plain(v: unknown): Cell {
  if (v === null || v === undefined) return null;
  if (typeof v === "string" || typeof v === "number") return v;
  if (typeof v === "boolean") return v ? 1 : 0;
  if (v instanceof Date) return v.toISOString();
  if (typeof v === "object") {
    const o = v as { result?: unknown; text?: unknown; richText?: { text: string }[] };
    if (o.richText) return o.richText.map((t) => t.text).join("");
    if (o.result !== undefined) return plain(o.result);
    if (o.text !== undefined) return plain(o.text);
  }
  return String(v);
}

/** All sheets as row arrays (first row = header), in workbook order. */
export async function readSheets(file: string): Promise<Cell[][][]> {
  const reader = new ExcelJS.stream.xlsx.WorkbookReader(file, { sharedStrings: "cache", worksheets: "emit" });
  const sheets: Cell[][][] = [];
  for await (const ws of reader) {
    const rows: Cell[][] = [];
    for await (const row of ws) {
      const values = row.values as unknown[];
      rows.push(values.slice(1).map(plain));
    }
    sheets.push(rows);
  }
  return sheets;
}

/** The sheet whose first row contains every one of `headers`. */
export function sheetWith(sheets: Cell[][][], ...headers: string[]): Cell[][] {
  const s = sheets.find((rows) => rows.length > 0 && headers.every((h) => rows[0].includes(h)));
  if (!s) throw new Error(`no sheet with columns ${headers.join(", ")}`);
  return s;
}
