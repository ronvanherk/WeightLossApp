import { describe, expect, it } from "vitest";
import { offToFood, servingLabel } from "./openfoodfacts";
import { searchText } from "./foods";

describe("offToFood", () => {
  it("maps names per language, the first brand and per-100g nutrients", () => {
    const f = offToFood({
      code: "5701234567890",
      product_name: "Skyr naturel",
      product_name_da: "Skyr naturel",
      brands: "Arla, Arla Foods",
      serving_quantity: "150",
      nutriments: { "energy-kcal_100g": 63, proteins_100g: 11, carbohydrates_100g: 4, fat_100g: "0,2" },
    });
    expect(f).toMatchObject({ source: "OFF", barcode: "5701234567890", nameDa: "Skyr naturel", nameEn: "Skyr naturel", brand: "Arla", kcal: 63, protein: 11, fat: 0.2, servingG: 150 });
  });

  it("derives kcal from kJ when only energy is given", () => {
    expect(offToFood({ code: "1", product_name: "x", nutriments: { energy_100g: 418.4 } })?.kcal).toBe(100);
  });

  it("reads the newer nutrition.aggregated_set format", () => {
    const f = offToFood({
      code: "1",
      product_name: "Himalaya Salz",
      nutriments: { "nova-group_100g": 2 },
      nutrition: { aggregated_set: { per: "100g", nutrients: { "energy-kcal": { value: 361 }, proteins: { value: 85.5 }, fat: { value: 1 } } } },
    });
    expect(f).toMatchObject({ kcal: 361, protein: 85.5, fat: 1 });
  });

  it("drops products without energy, without a name, or with impossible values", () => {
    expect(offToFood({ code: "1", product_name: "x", nutriments: {} })).toBeNull();
    expect(offToFood({ code: "1", nutriments: { "energy-kcal_100g": 50 } })).toBeNull();
    expect(offToFood({ code: "1", product_name: "x", nutriments: { "energy-kcal_100g": 4000 } })).toBeNull();
  });
});

describe("searchText", () => {
  it("joins brand and all names, lower-cased", () => {
    expect(searchText({ brand: "Arla", nameDa: "Skyr  Naturel", nameNl: null })).toBe("arla skyr naturel");
  });
});

describe("servingLabel", () => {
  it("keeps the words and drops the amount", () => {
    expect(servingLabel("1 slice (30 g)")).toBe("1 slice");
    expect(servingLabel("2 Scheiben (50g)")).toBe("2 Scheiben");
    expect(servingLabel("1 Portion = 30 g")).toBe("1 Portion");
    expect(servingLabel("30g (1 stuk)")).toBe("1 stuk");
    expect(servingLabel("1 glas (250 ml)")).toBe("1 glas");
  });

  it("is null when the pack only gives an amount", () => {
    expect(servingLabel("30 g")).toBeNull();
    expect(servingLabel("250ml")).toBeNull();
    expect(servingLabel("1,5 dl")).toBeNull();
    expect(servingLabel(undefined)).toBeNull();
  });
});
