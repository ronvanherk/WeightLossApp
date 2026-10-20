import { describe, expect, it } from "vitest";
import type { DaySummary } from "./summary";
import { onTarget, weekReview } from "./week";

const day = (kcal: number, targetKcal: number | null = 2000, extra: Partial<DaySummary> = {}): DaySummary => ({
  date: "2026-10-19",
  kcal,
  protein: kcal / 20,
  carbs: kcal / 10,
  fat: kcal / 30,
  waterMl: 0,
  targetKcal,
  steps: null,
  ...extra,
});

describe("onTarget", () => {
  it("counts 70% to 105% of the target", () => {
    expect(onTarget(day(1900))).toBe(true);
    expect(onTarget(day(2100))).toBe(true);
    expect(onTarget(day(2101))).toBe(false);
    expect(onTarget(day(1399))).toBe(false);
    expect(onTarget(day(0))).toBe(false);
    expect(onTarget(day(1800, null))).toBe(false);
  });
});

describe("weekReview", () => {
  it("averages over logged days only and compares with the week before", () => {
    const r = weekReview({
      days: [day(1800, 2000, { waterMl: 2000, steps: 8000 }), day(2400), day(0), day(2000, 2000, { waterMl: 1000 })],
      previousDays: [day(2500), day(2600)],
      weights: [90.2, 89.8],
      previousWeights: [90.6, 90.8],
      waistCm: 99,
      previousWaistCm: 100.5,
      fasts: [{ hours: 16.5, goalHours: 16 }, { hours: 12, goalHours: 16 }],
    });
    expect(r.daysCounted).toBe(4);
    expect(r.daysLogged).toBe(3);
    expect(r.daysOnTarget).toBe(2);
    expect(r.average.kcal).toBe(2067);
    expect(r.average.waterMl).toBe(1500);
    expect(r.average.steps).toBe(8000);
    expect(r.previous).toEqual({ kcal: 2550, daysLogged: 2, daysOnTarget: 0 });
    expect(r.weight).toEqual({ average: 90, previousAverage: 90.7, change: -0.7 });
    expect(r.waist).toEqual({ cm: 99, change: -1.5 });
    expect(r.fasting).toEqual({ count: 2, reached: 1 });
  });

  it("has no trend without data", () => {
    const r = weekReview({ days: [day(0)], previousDays: [], weights: [], previousWeights: [], waistCm: null, previousWaistCm: null, fasts: [] });
    expect(r.average.kcal).toBeNull();
    expect(r.weight.change).toBeNull();
    expect(r.waist.change).toBeNull();
  });
});
