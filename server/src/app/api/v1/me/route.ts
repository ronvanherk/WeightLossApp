import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { formatDay, parseDay } from "@/lib/dates";
import { householdOf } from "@/lib/diary";
import { LOCALES } from "@/lib/foods";
import { handler, HttpError, json, parseBody } from "@/lib/http";
import { targetFor } from "@/lib/targets";

export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const user = await db.user.findUnique({ where: { id: userId }, include: { profile: true } });
  if (!user) throw new HttpError(401, "user no longer exists");
  // The phone's date, so the weekend shift follows the user's clock, not the server's UTC.
  const raw = new URL(req.url).searchParams.get("today");
  const today = parseDay(raw ?? formatDay(new Date()));
  const p = user.profile;
  return json({
    user: { id: user.id, name: user.name, email: user.email },
    profile: p && {
      sex: p.sex,
      birthDate: formatDay(p.birthDate),
      heightCm: p.heightCm,
      activity: p.activity,
      goalWeightKg: p.goalWeightKg,
      weeklyLossKg: p.weeklyLossKg,
      locale: p.locale,
      proteinPct: p.proteinPct,
      fatPct: p.fatPct,
      carbsPct: p.carbsPct,
      fixedKcal: p.fixedKcal,
      weekendExtraKcal: p.weekendExtraKcal,
    },
    target: await targetFor(userId, today),
    household: await householdOf(userId),
  });
});

const Profile = z.object({
  sex: z.enum(["MALE", "FEMALE"]),
  birthDate: z.string(),
  heightCm: z.number().min(100).max(250),
  activity: z.enum(["SEDENTARY", "LIGHT", "MODERATE", "ACTIVE", "VERY_ACTIVE"]),
  goalWeightKg: z.number().min(30).max(300),
  // More than 1 kg a week is not something to plan for.
  weeklyLossKg: z.number().min(0).max(1),
  locale: z.enum(LOCALES),
  // Optional so an older app that does not know them leaves them as they are.
  proteinPct: z.number().int().min(10).max(70).optional(),
  fatPct: z.number().int().min(10).max(70).optional(),
  carbsPct: z.number().int().min(5).max(70).optional(),
  // null switches a fixed goal off again.
  fixedKcal: z.number().int().min(1000).max(5000).nullable().optional(),
  weekendExtraKcal: z.number().int().min(0).max(1000).optional(),
});

export const PUT = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, Profile);
  const split = [body.proteinPct, body.fatPct, body.carbsPct];
  if (split.some((v) => v !== undefined)) {
    if (split.some((v) => v === undefined)) throw new HttpError(400, "send proteinPct, fatPct and carbsPct together");
    if (split.reduce((a, b) => a! + b!, 0) !== 100) throw new HttpError(400, "Eiwit, vet en koolhydraten moeten samen 100% zijn.");
  }
  const data = { ...body, birthDate: parseDay(body.birthDate) };
  await db.profile.upsert({ where: { userId }, create: { userId, ...data }, update: data });
  return json({ ok: true });
});
