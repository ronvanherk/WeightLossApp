import { describe, expect, it } from "vitest";
import { offToFood } from "./openfoodfacts";
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
