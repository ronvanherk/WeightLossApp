// Usage: pnpm user:household <email> <email> [...]
// Puts these users in one household, so they can put meals in each other's diary.
// Uses the first user's id as the household id; a user can be in one household only.
import { db } from "../src/lib/db";

async function main() {
  const emails = process.argv.slice(2).map((e) => e.toLowerCase());
  if (emails.length < 2) {
    console.error("usage: pnpm user:household <email> <email> [...]");
    process.exit(1);
  }
  const users = await db.user.findMany({ where: { email: { in: emails } } });
  const missing = emails.filter((e) => !users.some((u) => u.email === e));
  if (missing.length) throw new Error(`no such user: ${missing.join(", ")}`);
  const householdId = users.find((u) => u.email === emails[0])!.id;
  await db.user.updateMany({ where: { email: { in: emails } }, data: { householdId } });
  console.log(`household ${householdId}: ${users.map((u) => u.name).join(", ")}`);
}

main().finally(() => db.$disconnect());
