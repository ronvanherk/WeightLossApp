import { db } from "./db";
import { ageOn } from "./dates";
import { dailyTarget, type DailyTarget } from "./nutrition";

/** The target for a day, using the latest weight on or before it; null until profile and a weight exist. */
export async function targetFor(userId: number, day: Date): Promise<DailyTarget | null> {
  const [profile, weight] = await Promise.all([
    db.profile.findUnique({ where: { userId } }),
    db.weightEntry.findFirst({ where: { userId, date: { lte: day } }, orderBy: { date: "desc" } }),
  ]);
  if (!profile || !weight) return null;
  return dailyTarget({
    sex: profile.sex,
    age: ageOn(profile.birthDate, day),
    heightCm: profile.heightCm,
    weightKg: weight.weightKg,
    activity: profile.activity,
    goalWeightKg: profile.goalWeightKg,
    weeklyLossKg: profile.weeklyLossKg,
  });
}

/** Targets for many days at once (charts), with two queries instead of two per day. */
export async function targetsFor(userId: number, days: Date[]): Promise<(DailyTarget | null)[]> {
  if (days.length === 0) return [];
  const last = days.reduce((a, b) => (a > b ? a : b));
  const [profile, weights] = await Promise.all([
    db.profile.findUnique({ where: { userId } }),
    db.weightEntry.findMany({ where: { userId, date: { lte: last } }, orderBy: { date: "asc" } }),
  ]);
  return days.map((day) => {
    const weight = weights.filter((w) => w.date <= day).at(-1);
    if (!profile || !weight) return null;
    return dailyTarget({
      sex: profile.sex,
      age: ageOn(profile.birthDate, day),
      heightCm: profile.heightCm,
      weightKg: weight.weightKg,
      activity: profile.activity,
      goalWeightKg: profile.goalWeightKg,
      weeklyLossKg: profile.weeklyLossKg,
    });
  });
}

export async function localeOf(userId: number): Promise<string> {
  const p = await db.profile.findUnique({ where: { userId }, select: { locale: true } });
  return p?.locale ?? "nl";
}
