import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { fastToApi, Goal } from "@/lib/fasting";
import { handler, HttpError, json, parseBody } from "@/lib/http";

/** The running fast, if any, and the last finished ones. */
export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const [current, recent] = await Promise.all([
    db.fast.findFirst({ where: { userId, endedAt: null } }),
    db.fast.findMany({ where: { userId, endedAt: { not: null } }, orderBy: { startedAt: "desc" }, take: 14 }),
  ]);
  return json({ current: current && fastToApi(current), recent: recent.map(fastToApi) });
});

const Body = z.object({
  goalHours: Goal,
  // Defaults to now; earlier when the fast began at the last meal.
  startedAt: z.iso.datetime({ offset: true }).optional(),
});

export const POST = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const body = await parseBody(req, Body);
  if (await db.fast.findFirst({ where: { userId, endedAt: null } })) throw new HttpError(409, "Er loopt al een vasten.");
  const startedAt = body.startedAt ? new Date(body.startedAt) : new Date();
  if (startedAt.getTime() > Date.now() + 60_000) throw new HttpError(400, "Het begin ligt in de toekomst.");
  const fast = await db.fast.create({ data: { userId, startedAt, goalHours: body.goalHours } });
  return json({ fast: fastToApi(fast) }, 201);
});
