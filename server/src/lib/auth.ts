import { SignJWT, jwtVerify } from "jose";
import { HttpError } from "./http";

const TOKEN_TTL = "180d";

function secret() {
  const s = process.env.AUTH_SECRET;
  if (!s || s.length < 32) throw new Error("AUTH_SECRET must be set (32+ chars)");
  return new TextEncoder().encode(s);
}

export async function issueToken(userId: number): Promise<string> {
  return new SignJWT({})
    .setProtectedHeader({ alg: "HS256" })
    .setSubject(String(userId))
    .setIssuedAt()
    .setExpirationTime(TOKEN_TTL)
    .sign(secret());
}

/** Returns the user id from the `Authorization: Bearer` header, or throws 401. */
export async function requireUser(req: Request): Promise<number> {
  const header = req.headers.get("authorization") ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : null;
  if (!token) throw new HttpError(401, "missing bearer token");
  try {
    const { payload } = await jwtVerify(token, secret(), { algorithms: ["HS256"] });
    const id = Number(payload.sub);
    if (!Number.isInteger(id)) throw new Error("bad sub");
    return id;
  } catch {
    throw new HttpError(401, "invalid or expired token");
  }
}
