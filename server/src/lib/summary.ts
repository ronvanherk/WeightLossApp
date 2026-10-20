import { db } from "./db";
import { formatDay } from "./dates";
import { targetsFor } from "./targets";

export const DAY_MS = 86_400_000;

export interface DaySummary {
  date: string;
  kcal: number;
  protein: number;
  carbs: number;
  fat: number;
  waterMl: number;
  targetKcal: number | null;
  steps: number | null;
}

/** The days from `from` to `to` inclusive. */
export function daysBetween(from: Date, to: Date): Date[] {
  const count = Math.round((to.getTime() - from.getTime()) / DAY_MS) + 1;
  return Array.from({ length: Math.max(0, count) }, (_, i) => new Date(from.getTime() + i * DAY_MS));
}

/** Per day: what was eaten and drunk, the steps and that day's kcal target. */
export async function daySummaries(userId: number, days: Date[]): Promise<DaySummary[]> {
  if (days.length === 0) return [];
  const from = days[0];
  const to = days[days.length - 1];
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
  return days.map((d, i) => {
    const s = sums.find((x) => x.date.getTime() === d.getTime())?._sum;
    return {
      date: formatDay(d),
      kcal: Math.round(s?.kcal ?? 0),
      protein: r(s?.protein),
      carbs: r(s?.carbs),
      fat: r(s?.fat),
      waterMl: water.find((w) => w.date.getTime() === d.getTime())?.ml ?? 0,
      targetKcal: targets[i]?.kcal ?? null,
      steps: targets[i]?.steps ?? null,
    };
  });
}
