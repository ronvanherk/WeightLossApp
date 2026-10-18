import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { formatDay, parseDay } from "@/lib/dates";
import { handler, HttpError, json } from "@/lib/http";
import { targetsFor } from "@/lib/targets";

const DAY_MS = 86_400_000;

/** Per day from `from` to `to` (max 366 days): what was eaten and drunk, and that day's kcal target. */
export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const params = new URL(req.url).searchParams;
  const from = parseDay(params.get("from"));
  const to = parseDay(params.get("to"));
  const count = Math.round((to.getTime() - from.getTime()) / DAY_MS) + 1;
  if (count < 1 || count > 366) throw new HttpError(400, "from..to must span 1 to 366 days");
  const days = Array.from({ length: count }, (_, i) => new Date(from.getTime() + i * DAY_MS));

  const [sums, water, targets] = await Promise.all([
    db.diaryEntry.groupBy({
      by: ["date"],
      where: { userId, date: { gte: from, lte: to } },
      _sum: { kcal: true, protein: true, carbs: true, fat: true },
    }),
    db.waterEntry.findMany({ where: { userId, date: { gte: from, lte: to } } }),
    targetsFor(userId, days),
  ]);
  const r = (v: number | null | undefined) => Math.round((v ?? 0) * 10) / 10;
  return json({
    days: days.map((d, i) => {
      const s = sums.find((x) => x.date.getTime() === d.getTime())?._sum;
      return {
        date: formatDay(d),
        kcal: Math.round(s?.kcal ?? 0),
        protein: r(s?.protein),
        carbs: r(s?.carbs),
        fat: r(s?.fat),
        waterMl: water.find((w) => w.date.getTime() === d.getTime())?.ml ?? 0,
        targetKcal: targets[i]?.kcal ?? null,
      };
    }),
  });
});
