import { db } from "./db";
import { formatDay } from "./dates";

/**
 * Days in a row with something logged, ending today, or yesterday while today is still empty
 * (a streak is not lost before the day is over).
 */
export function streakFrom(loggedDays: Set<string>, today: Date): number {
  const day = new Date(today);
  if (!loggedDays.has(formatDay(day))) day.setUTCDate(day.getUTCDate() - 1);
  let n = 0;
  while (loggedDays.has(formatDay(day))) {
    n++;
    day.setUTCDate(day.getUTCDate() - 1);
  }
  return n;
}

export async function streakOf(userId: number, today: Date): Promise<number> {
  const rows = await db.$queryRaw<{ date: Date }[]>`
    SELECT DISTINCT date FROM diary_entry
    WHERE user_id = ${userId} AND date <= ${today} AND date > ${today}::date - 400`;
  return streakFrom(new Set(rows.map((r) => formatDay(r.date))), today);
}
