import { HttpError } from "./http";

const DAY = /^\d{4}-\d{2}-\d{2}$/;

/** "2026-10-17" -> Date at UTC midnight, which is what Prisma stores in a @db.Date column. */
export function parseDay(raw: string | null | undefined): Date {
  if (!raw || !DAY.test(raw)) throw new HttpError(400, "date must be YYYY-MM-DD");
  const d = new Date(`${raw}T00:00:00Z`);
  if (Number.isNaN(d.getTime()) || formatDay(d) !== raw) throw new HttpError(400, "invalid date");
  return d;
}

export function formatDay(d: Date): string {
  return d.toISOString().slice(0, 10);
}

export function ageOn(birthDate: Date, on: Date): number {
  let age = on.getUTCFullYear() - birthDate.getUTCFullYear();
  const beforeBirthday =
    on.getUTCMonth() < birthDate.getUTCMonth() ||
    (on.getUTCMonth() === birthDate.getUTCMonth() && on.getUTCDate() < birthDate.getUTCDate());
  return beforeBirthday ? age - 1 : age;
}
