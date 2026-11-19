import { test } from "node:test";
import assert from "node:assert/strict";
import { findCatalogItem, itemLabel, unitLabel, ITEM_CATALOG, ITEM_CATEGORIES, UNIT_LABELS, RELIEF_TIER_LABELS } from "../src/lib/item-catalog";

for (const name of ["gạo", "Gao", " rice ", "gạo tẻ", "GAO TE"]) {
  test(`finds Rice from ${JSON.stringify(name)}`, () => {
    assert.equal(findCatalogItem(name)?.code, "RICE");
  });
}

test("matches historical seed names and Vietnamese đ", () => {
  assert.equal(findCatalogItem("Drinking Water")?.code, "BOTTLED_WATER");
  assert.equal(findCatalogItem("Water")?.code, "BOTTLED_WATER");
  assert.equal(findCatalogItem("Food")?.code, "DRY_RATIONS");
  assert.equal(findCatalogItem("Hygiene")?.code, "HYGIENE_KIT");
  assert.equal(findCatalogItem("Bo do bao ho")?.code, "PROTECTIVE_EQUIPMENT_KIT");
  assert.equal(findCatalogItem("Bo do dung sinh hoat")?.code, "HOUSEHOLD_KIT");
});

test("translates catalog names and preserves original English names", () => {
  assert.equal(itemLabel("Rice", "vi"), "Gạo");
  assert.equal(itemLabel("Rice", "en"), "Rice");
  assert.equal(itemLabel("Food", "en"), "Food");
  assert.equal(itemLabel("Drinking Water", "en"), "Drinking Water");
  assert.equal(itemLabel("Tên lạ", "vi"), "Tên lạ");
  assert.equal(itemLabel("Welcome back", "vi"), "Welcome back");
  assert.equal(findCatalogItem(""), undefined);
});

test("translates units and keeps historical codes and unknown units", () => {
  assert.equal(unitLabel("bag", "vi"), "bao");
  assert.equal(unitLabel("PACKAGE", "en"), "PACKAGE");
  assert.equal(unitLabel("KIT", "en"), "KIT");
  assert.equal(unitLabel("PACKAGE", "vi"), "gói");
  assert.equal(unitLabel("KIT", "vi"), "bộ");
  assert.equal(unitLabel("crate", "vi"), "crate");
  assert.equal(unitLabel("constructor", "vi"), "constructor");
});

test("all 33 catalog items have unique codes, names, valid groups, units and relief tiers", () => {
  assert.equal(ITEM_CATALOG.length, 33);
  assert.equal(ITEM_CATEGORIES.length, 12);
  assert.equal(new Set(ITEM_CATALOG.map(item => item.code)).size, ITEM_CATALOG.length);
  assert.equal(new Set(ITEM_CATALOG.map(item => item.nameEn)).size, ITEM_CATALOG.length);
  for (const item of ITEM_CATALOG) {
    assert.ok(ITEM_CATEGORIES.some(category => category.code === item.category));
    assert.ok(Object.hasOwn(UNIT_LABELS, item.unit));
    assert.ok(Object.hasOwn(RELIEF_TIER_LABELS, item.tier));
    for (const name of [item.nameEn, item.nameVi, ...(item.aliases ?? [])]) {
      assert.equal(findCatalogItem(name)?.code, item.code);
    }
  }
});


test("Rice, instant foods and the school supplies combo use their fixed units", () => {
  assert.equal(findCatalogItem("Gạo")?.unit, "kg");
  assert.equal(findCatalogItem("Instant noodles")?.unit, "box");
  assert.equal(findCatalogItem("Cháo ăn liền")?.unit, "box");
  assert.equal(findCatalogItem("Đồ dùng học tập")?.code, "SCHOOL_SUPPLIES_COMBO");
  assert.equal(findCatalogItem("Combo học tập")?.unit, "combo");
  assert.equal(itemLabel("School supplies combo", "vi"), "Combo đồ dùng học tập");
  assert.equal(unitLabel("combo", "vi"), "combo");
  assert.equal(unitLabel("combo", "en"), "combo");
  assert.equal(ITEM_CATALOG.filter(item => item.category === "EDUCATION").length, 1);
  for (const name of ["Notebooks", "Pens", "School bag", "Textbooks"]) {
    assert.equal(findCatalogItem(name), undefined);
    assert.equal(itemLabel(name, "vi"), name);
  }
});


test("relief bundles use fixed units and retired component names stay historical", () => {
  assert.equal(findCatalogItem("Gia vị")?.unit, "set");
  assert.equal(findCatalogItem("Nước uống")?.unit, "box");
  assert.equal(findCatalogItem("Bộ vệ sinh cá nhân")?.unit, "kit");
  const bundles = ["Water treatment kit", "Women's hygiene kit", "Warm clothing set", "Bedding kit",
    "Household supplies kit", "Baby care kit", "Elderly care kit", "Emergency lighting kit",
    "Emergency shelter kit", "Protective equipment kit", "Cooking utensils kit"];
  for (const name of bundles) assert.equal(findCatalogItem(name)?.unit, "set", name);
  assert.equal(findCatalogItem("First aid kit")?.unit, "kit");
  assert.equal(findCatalogItem("Canned food")?.unit, "box");
  assert.equal(findCatalogItem("Dry rations")?.unit, "box");
  assert.equal(findCatalogItem("Soft food")?.unit, "box");
  for (const name of ["Salt", "Sugar", "Cooking oil", "Fish sauce", "Soap", "Toilet paper", "Bandages", "Water jug", "Flashlight"]) {
    assert.equal(findCatalogItem(name), undefined);
    assert.equal(itemLabel(name, "vi"), name);
  }
  assert.deepEqual(ITEM_CATALOG.filter(item => item.category === "HYGIENE").map(item => item.code),
    ["HYGIENE_KIT", "WOMENS_HYGIENE_KIT"]);
});
