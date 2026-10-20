import type { DaySummary } from "./summary";

/**
 * A day is within the target when something was logged and it lands between 70% and 105% of
 * the target: a little over is fine, and far under is more likely a forgotten meal than a win.
 */
export function onTarget(d: DaySummary): boolean {
  if (d.kcal <= 0 || d.targetKcal == null) return false;
  return d.kcal >= d.targetKcal * 0.7 && d.kcal <= d.targetKcal * 1.05;
}

const avg = (values: number[]) => (values.length ? values.reduce((a, b) => a + b, 0) / values.length : null);
const r0 = (v: number | null) => (v == null ? null : Math.round(v));
const r1 = (v: number | null) => (v == null ? null : Math.round(v * 10) / 10);

export interface WeekInput {
  /** Monday to Sunday, up to today; a week in progress has fewer days. */
  days: DaySummary[];
  previousDays: DaySummary[];
  weights: number[];
  previousWeights: number[];
  /** Waist at the end of this week and before it, if measured. */
  waistCm: number | null;
  previousWaistCm: number | null;
  fasts: { hours: number; goalHours: number }[];
}

export function weekReview(w: WeekInput) {
  const logged = w.days.filter((d) => d.kcal > 0);
  const prevLogged = w.previousDays.filter((d) => d.kcal > 0);
  const weightAvg = avg(w.weights);
  const prevWeightAvg = avg(w.previousWeights);
  return {
    daysCounted: w.days.length,
    daysLogged: logged.length,
    daysOnTarget: w.days.filter(onTarget).length,
    average: {
      kcal: r0(avg(logged.map((d) => d.kcal))),
      targetKcal: r0(avg(logged.flatMap((d) => (d.targetKcal == null ? [] : [d.targetKcal])))),
      protein: r1(avg(logged.map((d) => d.protein))),
      carbs: r1(avg(logged.map((d) => d.carbs))),
      fat: r1(avg(logged.map((d) => d.fat))),
      waterMl: r0(avg(w.days.filter((d) => d.waterMl > 0).map((d) => d.waterMl))),
      steps: r0(avg(w.days.flatMap((d) => (d.steps ? [d.steps] : [])))),
    },
    previous: {
      kcal: r0(avg(prevLogged.map((d) => d.kcal))),
      daysLogged: prevLogged.length,
      daysOnTarget: w.previousDays.filter(onTarget).length,
    },
    weight: {
      average: r1(weightAvg),
      previousAverage: r1(prevWeightAvg),
      // Week average against week average, so one salty evening does not set the trend.
      change: weightAvg != null && prevWeightAvg != null ? r1(weightAvg - prevWeightAvg) : null,
    },
    waist: {
      cm: w.waistCm,
      change: w.waistCm != null && w.previousWaistCm != null ? r1(w.waistCm - w.previousWaistCm) : null,
    },
    fasting: { count: w.fasts.length, reached: w.fasts.filter((f) => f.hours >= f.goalHours).length },
  };
}
