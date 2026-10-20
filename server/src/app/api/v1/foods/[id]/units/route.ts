import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db } from "@/lib/db";
import { handler, HttpError, json, parseBody, parseId } from "@/lib/http";

const Body = z.object({ name: z.string().trim().min(1).max(40), grams: z.number().positive().max(5000) });

/** A named amount of this food ("1 boterham" = 35 g); everyone sees it, like custom products. */
export const POST = handler(async (req: Request, ctx: { params: Promise<{ id: string }> }) => {
  const userId = await requireUser(req);
  const foodId = parseId((await ctx.params).id);
  const body = await parseBody(req, Body);
  if (!(await db.food.findUnique({ where: { id: foodId }, select: { id: true } }))) throw new HttpError(404, "no such food");
  const taken = await db.foodUnit.findUnique({ where: { foodId_name: { foodId, name: body.name } } });
  if (taken) throw new HttpError(409, `"${body.name}" bestaat al voor dit product.`);
  const unit = await db.foodUnit.create({ data: { foodId, name: body.name, grams: body.grams, createdById: userId } });
  return json({ id: unit.id, name: unit.name, grams: unit.grams, mine: true }, 201);
});
