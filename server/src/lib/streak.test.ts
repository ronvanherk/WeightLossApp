import { describe, expect, it } from "vitest";
import { streakFrom } from "./streak";

const today = new Date("2026-10-19T00:00:00Z");

describe("streakFrom", () => {
  it("counts back from today", () => {
    expect(streakFrom(new Set(["2026-10-19", "2026-10-18", "2026-10-17", "2026-10-15"]), today)).toBe(3);
  });
  it("keeps yesterday's streak while today is empty", () => {
    expect(streakFrom(new Set(["2026-10-18", "2026-10-17"]), today)).toBe(2);
  });
  it("is zero after a missed day", () => {
    expect(streakFrom(new Set(["2026-10-17"]), today)).toBe(0);
  });
});
