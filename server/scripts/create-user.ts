// Usage: pnpm user:create <email> <name> <password>
// There is no sign-up; the household's accounts are created here.
import bcrypt from "bcryptjs";
import { db } from "../src/lib/db";

async function main() {
  const [email, name, password] = process.argv.slice(2);
  if (!email || !name || !password) {
    console.error("usage: pnpm user:create <email> <name> <password>");
    process.exit(1);
  }
  if (password.length < 10) throw new Error("use a password of at least 10 characters");
  const passwordHash = await bcrypt.hash(password, 12);
  const user = await db.user.upsert({
    where: { email: email.toLowerCase() },
    create: { email: email.toLowerCase(), name, passwordHash },
    update: { name, passwordHash },
  });
  console.log(`user ${user.id} <${user.email}> ready`);
}

main().finally(() => db.$disconnect());
