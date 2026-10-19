import { requireUser } from "@/lib/auth";
import { householdOf } from "@/lib/diary";
import { handler, json } from "@/lib/http";

/** Who else is in this user's household, to share a meal with. */
export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  return json({ members: await householdOf(userId) });
});
