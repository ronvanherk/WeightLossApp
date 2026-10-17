import { describe, expect, it } from "vitest";
import { bmr, dailyTarget, portion } from "./nutrition";

describe("bmr", () => {
  it("matches Mifflin-St Jeor reference values", () => {
    expect(bmr("MALE", 90, 185, 50)).toBeCloseTo(1811.25);
    expect(bmr("FEMALE", 70, 168, 48)).toBeCloseTo(1349);
  });
});

describe("dailyTarget", () => {
  const base = { sex: "MALE" as const, age: 50, heightCm: 185, weightKg: 90, activity: "LIGHT" as const, goalWeightKg: 80 };

  it("subtracts the planned weekly loss from TDEE", () => {
    const t = dailyTarget({ ...base, weeklyLossKg: 0.5 });
    expect(t.tdee).toBe(2490); // 1811.25 * 1.375
    expect(t.kcal).toBe(1940); // 2490 - 550
    expect(t.clamped).toBe(false);
  });

  it("maintains once the goal is reached", () => {
    const t = dailyTarget({ ...base, weightKg: 79, weeklyLossKg: 0.5 });
    expect(t.kcal).toBe(t.tdee);
  });

  it("never goes below the minimum", () => {
    const t = dailyTarget({ ...base, sex: "FEMALE", weightKg: 60, heightCm: 160, goalWeightKg: 55, activity: "SEDENTARY", weeklyLossKg: 1 });
    expect(t.kcal).toBe(1200);
    expect(t.clamped).toBe(true);
  });

  it("splits macros 25/30/45", () => {
    const t = dailyTarget({ ...base, weeklyLossKg: 0.5 });
    expect(t.proteinG * 4 + t.fatG * 9 + t.carbsG * 4).toBeGreaterThan(t.kcal - 10);
    expect(t.proteinG * 4 + t.fatG * 9 + t.carbsG * 4).toBeLessThan(t.kcal + 10);
  });
});

describe("portion", () => {
  it("scales per-100g values", () => {
    expect(portion({ kcal: 250, protein: 10, carbs: null, fat: 3.4 }, 150)).toEqual({ kcal: 375, protein: 15, carbs: 0, fat: 5.1 });
  });
});
