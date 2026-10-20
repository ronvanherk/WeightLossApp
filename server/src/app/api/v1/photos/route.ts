import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { formatDay, parseDay } from "@/lib/dates";
import { handler, HttpError, json } from "@/lib/http";
import { isJpeg, MAX_PHOTO_BYTES, savePhoto } from "@/lib/photos";

/** This user's progress photos, newest first; the images themselves come from photos/[id]. */
export const GET = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const rows = await db.progressPhoto.findMany({ where: { userId }, orderBy: [{ date: "desc" }, { id: "desc" }] });
  return json({ photos: rows.map((p) => ({ id: p.id, date: formatDay(p.date) })) });
});

/** Multipart: `date` (YYYY-MM-DD) and `photo` (a JPEG of at most 1 MB). */
export const POST = handler(async (req: Request) => {
  const userId = await requireUser(req);
  let form: FormData;
  try {
    form = await req.formData();
  } catch {
    throw new HttpError(400, "body must be multipart/form-data");
  }
  const date = parseDay(form.get("date")?.toString());
  const photo = form.get("photo");
  if (!(photo instanceof Blob)) throw new HttpError(400, "photo is missing");
  if (photo.size > MAX_PHOTO_BYTES) throw new HttpError(413, "De foto is te groot.");
  const bytes = new Uint8Array(await photo.arrayBuffer());
  if (!isJpeg(bytes)) throw new HttpError(400, "photo must be a JPEG");
  const file = await savePhoto(userId, bytes);
  const row = await db.progressPhoto.create({ data: { userId, date, file } });
  return json({ id: row.id, date: formatDay(row.date) }, 201);
});
