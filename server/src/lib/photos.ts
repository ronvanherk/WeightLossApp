import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { randomUUID } from "node:crypto";
import path from "node:path";

/** Progress photos stay on this server's disk (a Docker volume), outside the database. */
const DIR = process.env.PHOTO_DIR ?? "/data/photos";

/** nginx accepts 1 MB per request; the app shrinks photos well below that. */
export const MAX_PHOTO_BYTES = 1_000_000;

export function isJpeg(bytes: Uint8Array): boolean {
  return bytes.length > 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
}

/** Stores the bytes and returns the path to keep in the database. */
export async function savePhoto(userId: number, bytes: Uint8Array): Promise<string> {
  const file = `${userId}/${randomUUID()}.jpg`;
  await mkdir(path.join(DIR, String(userId)), { recursive: true });
  await writeFile(path.join(DIR, file), bytes);
  return file;
}

export function readPhoto(file: string): Promise<Buffer> {
  return readFile(path.join(DIR, file));
}

export async function deletePhoto(file: string): Promise<void> {
  await rm(path.join(DIR, file), { force: true });
}
