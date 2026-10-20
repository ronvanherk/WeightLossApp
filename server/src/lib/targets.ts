import { db } from "./db";
import { ageOn } from "./dates";
import type { Profile } from "@prisma/client";
import { dailyTarget, type DailyTarget } from "./nutrition";

/** The target of one day from the profile, the weight that applies and that day's steps. */
function targetOn(profile: Profile, day: Date, weightKg: number, steps: number | null): DailyTarget {
  return dailyTarget({
    sex: profile.sex,
    age: ageOn(profile.birthDate, day),
    heightCm: profile.heightCm,
    weightKg,
    activity: profile.activity,
    goalWeightKg: profile.goalWeightKg,
    weeklyLossKg: profile.weeklyLossKg,
    steps,
    split: { proteinPct: profile.proteinPct, fatPct: profile.fatPct, carbsPct: profile.carbsPct },
    fixedKcal: profile.fixedKcal,
    weekendExtraKcal: profile.weekendExtraKcal,
    // Days are stored as UTC midnight of the calendar date.
    weekday: day.getUTCDay(),
  });
}

/** The target for a day, using the latest weight on or before it; null until profile and a weight exist. */
export async function targetFor(userId: number, day: Date): Promise<DailyTarget | null> {
  const [profile, weight, steps] = await Promise.all([
    db.profile.findUnique({ where: { userId } }),
    db.weightEntry.findFirst({ where: { userId, date: { lte: day } }, orderBy: { date: "desc" } }),
    db.dailySteps.findUnique({ where: { userId_date: { userId, date: day } } }),
  ]);
  if (!profile || !weight) return null;
  return targetOn(profile, day, weight.weightKg, steps?.steps ?? null);
}

/** Targets for many days at once (charts), with two queries instead of two per day. */
export async function targetsFor(userId: number, days: Date[]): Promise<(DailyTarget | null)[]> {
  if (days.length === 0) return [];
  const last = days.reduce((a, b) => (a > b ? a : b));
  const first = days.reduce((a, b) => (a < b ? a : b));
  const [profile, weights, steps] = await Promise.all([
    db.profile.findUnique({ where: { userId } }),
    db.weightEntry.findMany({ where: { userId, date: { lte: last } }, orderBy: { date: "asc" } }),
    db.dailySteps.findMany({ where: { userId, date: { gte: first, lte: last } } }),
  ]);
  return days.map((day) => {
    const weight = weights.filter((w) => w.date <= day).at(-1);
    if (!profile || !weight) return null;
    return targetOn(profile, day, weight.weightKg, steps.find((s) => s.date.getTime() === day.getTime())?.steps ?? null);
  });
}

export async function localeOf(userId: number): Promise<string> {
  const p = await db.profile.findUnique({ where: { userId }, select: { locale: true } });
  return p?.locale ?? "nl";
}
