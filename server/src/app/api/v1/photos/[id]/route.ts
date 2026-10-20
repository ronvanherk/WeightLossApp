import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { handler, HttpError, json, parseId } from "@/lib/http";
import { deletePhoto, readPhoto } from "@/lib/photos";

async function ownPhoto(req: Request, ctx: { params: Promise<{ id: string }> }) {
  const userId = await requireUser(req);
  const id = parseId((await ctx.params).id);
  const photo = await db.progressPhoto.findFirst({ where: { id, userId } });
  // Someone else's photo is reported as missing, as with diary entries.
  if (!photo) throw new HttpError(404, "no such photo");
  return photo;
}

/** The JPEG itself; only for its owner. A photo never changes, so the phone may cache it. */
export const GET = handler(async (req: Request, ctx: { params: Promise<{ id: string }> }) => {
  const photo = await ownPhoto(req, ctx);
  let bytes: Buffer;
  try {
    bytes = await readPhoto(photo.file);
  } catch {
    throw new HttpError(404, "photo file is gone");
  }
  return new Response(new Uint8Array(bytes), {
    headers: { "Content-Type": "image/jpeg", "Cache-Control": "private, max-age=31536000, immutable" },
  });
});

export const DELETE = handler(async (req: Request, ctx: { params: Promise<{ id: string }> }) => {
  const photo = await ownPhoto(req, ctx);
  await db.progressPhoto.delete({ where: { id: photo.id } });
  await deletePhoto(photo.file);
  return json({ ok: true });
});
