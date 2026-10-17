import { randomUUID } from "node:crypto";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { searchText, toApi } from "@/lib/foods";
import { handler, json, parseBody } from "@/lib/http";
import { localeOf } from "@/lib/targets";

const g = z.number().min(0).max(100).nullable().optional();
const Body = z.object({
  name: z.string().trim().min(1).max(200),
  brand: z.string().trim().max(100).nullable().optional(),
  barcode: z.string().regex(/^\d{6,14}$/).nullable().optional(),
  kcal: z.number().min(0).max(900),
  protein: g,
  carbs: g,
  fat: g,
  fiber: g,
  sugar: g,
  salt: g,
  servingG: z.number().positive().max(5000).nullable().optional(),
});

/** A product entered by hand; shared with the other users of the app. */
export const POST = handler(async (req: Request) => {
  const userId = await requireUser(req);
  const { name, ...rest } = await parseBody(req, Body);
  const locale = await localeOf(userId);
  const names = { nameNl: null, nameDa: null, nameDe: null, nameEn: null } as Record<string, string | null>;
  names[{ nl: "nameNl", da: "nameDa", de: "nameDe" }[locale] ?? "nameNl"] = name;
  const food = await db.food.create({
    data: {
      source: "CUSTOM",
      sourceId: randomUUID(),
      ...names,
      ...rest,
      searchText: searchText({ ...names, brand: rest.brand }),
      createdById: userId,
    },
  });
  return json({ food: toApi(food, locale) }, 201);
});
