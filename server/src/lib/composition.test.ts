import { describe, expect, it } from "vitest";
import { baseUnit, cellValue, convert, mainValues } from "./composition";

describe("composition helpers", () => {
  it("reads units", () => {
    expect(baseUnit("mg/100g")).toBe("mg");
    expect(baseUnit("kcal/100 g")).toBe("kcal");
    expect(baseUnit("ug")).toBe("µg");
  });
  it("converts mass units only", () => {
    expect(convert(1500, "µg", "mg")).toBeCloseTo(1.5);
    expect(convert(2, "g", "mg")).toBe(2000);
    expect(convert(5, "kcal", "kcal")).toBe(5);
    expect(convert(5, "kJ", "kcal")).toBeNull();
  });
  it("maps markers", () => {
    expect(cellValue("-")).toBeNull();
    expect(cellValue("TR")).toBe(0);
    expect(cellValue("<LOD or <LOQ")).toBe(0);
    expect(cellValue("NULL")).toBeNull();
    expect(cellValue("1,5")).toBe(1.5);
    expect(cellValue(3)).toBe(3);
  });
  it("derives salt from sodium", () => {
    expect(mainValues({ ENERCC: 100, NA: 400 }).salt).toBe(1);
    expect(mainValues({ ENERCC: 100, NA: 400 }, 0.8).salt).toBe(0.8);
  });
});
