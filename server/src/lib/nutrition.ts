import type { ActivityLevel, Sex } from "@prisma/client";

/** kcal in one kg of body fat, the usual planning figure. */
export const KCAL_PER_KG = 7700;

const ACTIVITY_FACTOR: Record<ActivityLevel, number> = {
  SEDENTARY: 1.2,
  LIGHT: 1.375,
  MODERATE: 1.55,
  ACTIVE: 1.725,
  VERY_ACTIVE: 1.9,
};

/** Below this the target is not lowered further, however fast the plan. */
const MIN_KCAL: Record<Sex, number> = { MALE: 1500, FEMALE: 1200 };

export interface TargetInput {
  sex: Sex;
  age: number;
  heightCm: number;
  weightKg: number;
  activity: ActivityLevel;
  goalWeightKg: number;
  weeklyLossKg: number;
  /** Synced from the phone; null when unknown. */
  steps?: number | null;
}

export interface DailyTarget {
  bmr: number;
  tdee: number;
  kcal: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  /** True when the minimum, not the plan, set the target. */
  clamped: boolean;
  steps: number | null;
  /** Extra kcal for steps beyond what the activity level already counts. */
  stepsKcal: number;
}

/**
 * Steps the activity level already assumes on an average day. Only steps above this earn extra
 * kcal, so a walk is not counted twice.
 */
export const BASELINE_STEPS: Record<ActivityLevel, number> = {
  SEDENTARY: 3000,
  LIGHT: 5000,
  MODERATE: 7500,
  ACTIVE: 10000,
  VERY_ACTIVE: 12000,
};

/** Walking costs about 0.5 kcal per kg body weight per 1000 steps. */
export function stepsKcal(steps: number | null | undefined, weightKg: number, activity: ActivityLevel): number {
  if (!steps) return 0;
  return Math.round(Math.max(0, steps - BASELINE_STEPS[activity]) * weightKg * 0.0005);
}

/** Mifflin-St Jeor resting energy expenditure. */
export function bmr(sex: Sex, weightKg: number, heightCm: number, age: number): number {
  return 10 * weightKg + 6.25 * heightCm - 5 * age + (sex === "MALE" ? 5 : -161);
}

export function dailyTarget(i: TargetInput): DailyTarget {
  const rest = bmr(i.sex, i.weightKg, i.heightCm, i.age);
  const tdee = rest * ACTIVITY_FACTOR[i.activity];
  const losing = i.weightKg > i.goalWeightKg && i.weeklyLossKg > 0;
  const extra = stepsKcal(i.steps, i.weightKg, i.activity);
  const planned = (losing ? tdee - (i.weeklyLossKg * KCAL_PER_KG) / 7 : tdee) + extra;
  const kcal = Math.max(planned, MIN_KCAL[i.sex]);
  // Split 25% protein / 30% fat / 45% carbs; 4 / 9 / 4 kcal per gram.
  return {
    bmr: Math.round(rest),
    tdee: Math.round(tdee),
    kcal: Math.round(kcal),
    proteinG: Math.round((kcal * 0.25) / 4),
    fatG: Math.round((kcal * 0.3) / 9),
    carbsG: Math.round((kcal * 0.45) / 4),
    clamped: planned < MIN_KCAL[i.sex],
    steps: i.steps ?? null,
    stepsKcal: extra,
  };
}

export interface Per100 {
  kcal: number;
  protein: number | null;
  carbs: number | null;
  fat: number | null;
}

/** Nutrients for a logged portion, from per-100g values. */
export function portion(food: Per100, grams: number) {
  const f = grams / 100;
  const r = (v: number | null) => Math.round((v ?? 0) * f * 10) / 10;
  return { kcal: Math.round(food.kcal * f), protein: r(food.protein), carbs: r(food.carbs), fat: r(food.fat) };
}
