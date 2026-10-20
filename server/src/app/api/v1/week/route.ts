import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { formatDay, parseDay } from "@/lib/dates";
import { handler, json } from "@/lib/http";
import { DAY_MS, daySummaries, daysBetween } from "@/lib/summary";
import { weekReview } from "@/lib/week";

/** Monday of the week that holds `day`. */
function mondayOf(day: Date): Date {
  return new Date(day.getTime() - ((day.getUTCDay() + 6) % 7) * DAY_MS);
}

/**
 * The week (Monday to Sunday) holding `date`, compared with the week before: averages, days
 * within the target, weight and waist trend, fasts. `today` is the phone's date; days after it
 * do not count yet.
 */
export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const params = new URL(req.url).searchParams;
  const today = parseDay(params.get("today") ?? formatDay(new Date()));
  const start = mondayOf(parseDay(params.get("date") ?? formatDay(today)));
  const end = new Date(start.getTime() + 6 * DAY_MS);
  const until = end < today ? end : today;
  const prevStart = new Date(start.getTime() - 7 * DAY_MS);
  const prevEnd = new Date(start.getTime() - DAY_MS);

  const [days, previousDays, weights, waistNow, waistBefore, fasts] = await Promise.all([
    daySummaries(userId, daysBetween(start, until)),
    daySummaries(userId, daysBetween(prevStart, prevEnd)),
    db.weightEntry.findMany({ where: { userId, date: { gte: prevStart, lte: end } }, orderBy: { date: "asc" } }),
    db.bodyMeasurement.findFirst({ where: { userId, date: { gte: start, lte: end }, waistCm: { not: null } }, orderBy: { date: "desc" } }),
    db.bodyMeasurement.findFirst({ where: { userId, date: { lt: start }, waistCm: { not: null } }, orderBy: { date: "desc" } }),
    db.fast.findMany({ where: { userId, endedAt: { not: null }, startedAt: { gte: start, lt: new Date(end.getTime() + DAY_MS) } } }),
  ]);
  const review = weekReview({
    days,
    previousDays,
    weights: weights.filter((w) => w.date >= start).map((w) => w.weightKg),
    previousWeights: weights.filter((w) => w.date < start).map((w) => w.weightKg),
    waistCm: waistNow?.waistCm ?? null,
    previousWaistCm: waistBefore?.waistCm ?? null,
    fasts: fasts.map((f) => ({ hours: (f.endedAt!.getTime() - f.startedAt.getTime()) / 3_600_000, goalHours: f.goalHours })),
  });
  return json({ start: formatDay(start), end: formatDay(end), days, ...review });
});
