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
  const today = parseDay(formatDay(new Date()));
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
});

export const PUT = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, Profile);
  const data = { ...body, birthDate: parseDay(body.birthDate) };
  await db.profile.upsert({ where: { userId }, create: { userId, ...data }, update: data });
  return json({ ok: true });
});
