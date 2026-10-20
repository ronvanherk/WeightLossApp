import { requireUser } from "@/lib/auth";
import { parseDay } from "@/lib/dates";
import { handler, HttpError, json } from "@/lib/http";
import { daySummaries, daysBetween } from "@/lib/summary";

/** Per day from `from` to `to` (max 366 days): what was eaten and drunk, and that day's kcal target. */
export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const params = new URL(req.url).searchParams;
  const days = daysBetween(parseDay(params.get("from")), parseDay(params.get("to")));
  if (days.length < 1 || days.length > 366) throw new HttpError(400, "from..to must span 1 to 366 days");
  return json({ days: await daySummaries(userId, days) });
});
