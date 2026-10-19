import { describe, expect, it } from "vitest";
import { bmr, dailyTarget, portion, portionExtras } from "./nutrition";

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

  it("adds kcal for steps above the activity level's baseline", () => {
    const plain = dailyTarget({ ...base, weeklyLossKg: 0.5 });
    const walked = dailyTarget({ ...base, weeklyLossKg: 0.5, steps: 13000 });
    expect(walked.stepsKcal).toBe(360); // (13000 - 5000) * 90 * 0.0005
    expect(walked.kcal).toBe(plain.kcal + 360);
    expect(dailyTarget({ ...base, weeklyLossKg: 0.5, steps: 4000 }).stepsKcal).toBe(0);
    expect(plain.steps).toBeNull();
  });

  it("sets limits for fiber, sugar, saturated fat and salt", () => {
    const t = dailyTarget({ ...base, weeklyLossKg: 0.5 });
    expect(t.fiberG).toBe(35); // 2490 * 0.014
    expect(t.sugarMaxG).toBe(87); // 1940 * 0.18 / 4
    expect(t.saturatedFatMaxG).toBe(22); // 1940 * 0.1 / 9
    expect(t.saltMaxG).toBe(6);
    const small = dailyTarget({ ...base, sex: "FEMALE", weightKg: 60, heightCm: 160, goalWeightKg: 55, activity: "SEDENTARY", weeklyLossKg: 1 });
    expect(small.fiberG).toBe(25);
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

describe("portionExtras", () => {
  it("scales known values and keeps unknown ones null", () => {
    expect(portionExtras({ fiber: 6.5, sugar: null, saturatedFat: 1.23, salt: 1.234 }, 50)).toEqual({
      fiber: 3.3,
      sugar: null,
      saturatedFat: 0.6,
      salt: 0.62,
    });
  });
});
