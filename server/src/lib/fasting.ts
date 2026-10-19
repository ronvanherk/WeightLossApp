import type { Fast } from "@prisma/client";
import { z } from "zod";

export function fastToApi(f: Fast) {
  return { id: f.id, startedAt: f.startedAt.toISOString(), endedAt: f.endedAt?.toISOString() ?? null, goalHours: f.goalHours };
}

export const Goal = z.number().int().min(1).max(72);
