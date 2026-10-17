import bcrypt from "bcryptjs";
import { z } from "zod";
import { issueToken } from "@/lib/auth";
import { db } from "@/lib/db";
import { handler, HttpError, json, parseBody } from "@/lib/http";

const Body = z.object({ email: z.string().email(), password: z.string().min(1) });

export const POST = handler(async (req: Request) => {
  const { email, password } = await parseBody(req, Body);
  const user = await db.user.findUnique({ where: { email: email.toLowerCase() } });
  if (!user || !(await bcrypt.compare(password, user.passwordHash))) throw new HttpError(401, "wrong email or password");
  return json({ token: await issueToken(user.id), user: { id: user.id, name: user.name, email: user.email } });
});
