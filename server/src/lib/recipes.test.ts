import { describe, expect, it } from "vitest";
import { combine } from "./recipes";

const food = (kcal: number, protein: number | null, nutrients: Record<string, number> | null = null) => ({
  kcal,
  protein,
  carbs: null,
  fat: null,
  fiber: null,
  sugar: null,
  salt: null,
  saturatedFat: null,
  nutrients,
});

describe("combine", () => {
  it("averages by weight over the raw total", () => {
    const c = combine([{ food: food(100, 10), grams: 100 }, { food: food(300, null), grams: 100 }], null);
    expect(c.kcal).toBe(200);
    expect(c.protein).toBe(5); // null counts as 0 when another ingredient has a value
    expect(c.carbs).toBeNull(); // null everywhere stays null
    expect(c.shares).toEqual([50, 50]);
  });

  it("uses the cooked weight when given", () => {
    // 100 g dry pasta (350 kcal) cooks to 250 g
    const c = combine([{ food: food(350, 12), grams: 100 }], 250);
    expect(c.kcal).toBe(140);
    expect(c.protein).toBe(4.8);
    expect(c.rawG).toBe(100);
  });

  it("keeps a detailed nutrient only when every ingredient has it", () => {
    const c = combine(
      [{ food: food(100, 1, { VITC: 10, FE: 2 }), grams: 50 }, { food: food(100, 1, { VITC: 30 }), grams: 50 }],
      null,
    );
    expect(c.nutrients).toEqual({ VITC: 20 });
  });
});
