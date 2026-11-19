export type ReliefTier = "P1" | "P2" | "P3";
export type CategoryCode = "FOOD" | "WATER" | "HYGIENE" | "CLOTHING" | "BEDDING_HOUSEHOLD" | "MEDICAL" | "CHILDREN" | "ELDERLY" | "ESSENTIAL_EQUIPMENT" | "DISASTER_PREP" | "COOKING" | "EDUCATION";
export type CatalogItem = {
  code: string;
  category: CategoryCode;
  nameEn: string;
  nameVi: string;
  unit: string;
  tier: ReliefTier;
  aliases?: string[];
};

export const ITEM_CATEGORIES: { code: CategoryCode; nameEn: string; nameVi: string }[] = [
  { code: "FOOD", nameEn: "Food", nameVi: "Lương thực, thực phẩm" },
  { code: "WATER", nameEn: "Drinking water", nameVi: "Nước uống" },
  { code: "HYGIENE", nameEn: "Personal hygiene", nameVi: "Đồ vệ sinh cá nhân" },
  { code: "CLOTHING", nameEn: "Clothing", nameVi: "Quần áo" },
  { code: "BEDDING_HOUSEHOLD", nameEn: "Bedding & household", nameVi: "Chăn màn, đồ sinh hoạt" },
  { code: "MEDICAL", nameEn: "Medical supplies", nameVi: "Vật dụng y tế" },
  { code: "CHILDREN", nameEn: "Children's supplies", nameVi: "Đồ dùng trẻ em" },
  { code: "ELDERLY", nameEn: "Elderly care", nameVi: "Đồ dùng người cao tuổi" },
  { code: "ESSENTIAL_EQUIPMENT", nameEn: "Essential equipment", nameVi: "Thiết bị thiết yếu" },
  { code: "DISASTER_PREP", nameEn: "Disaster preparedness", nameVi: "Vật tư phòng chống thiên tai" },
  { code: "COOKING", nameEn: "Cooking utensils", nameVi: "Dụng cụ nấu ăn" },
  { code: "EDUCATION", nameEn: "School supplies", nameVi: "Đồ dùng học tập" },
];

export const ITEM_CATALOG: readonly CatalogItem[] = [
  { code: "RICE", category: "FOOD", nameEn: "Rice", nameVi: "Gạo", unit: "kg", tier: "P2", aliases: ["gao", "gạo tẻ"] },
  { code: "INSTANT_NOODLES", category: "FOOD", nameEn: "Instant noodles", nameVi: "Mì ăn liền", unit: "box", tier: "P2" },
  { code: "INSTANT_PORRIDGE", category: "FOOD", nameEn: "Instant porridge", nameVi: "Cháo ăn liền", unit: "box", tier: "P1" },
  { code: "DRY_RATIONS", category: "FOOD", nameEn: "Dry rations", nameVi: "Lương khô", unit: "box", tier: "P1", aliases: ["Food"] },
  { code: "BREAD", category: "FOOD", nameEn: "Bread", nameVi: "Bánh mì", unit: "pack", tier: "P1" },
  { code: "CANNED_FOOD", category: "FOOD", nameEn: "Canned food", nameVi: "Thực phẩm đóng hộp", unit: "box", tier: "P1" },
  { code: "UHT_MILK", category: "FOOD", nameEn: "UHT milk", nameVi: "Sữa hộp", unit: "box", tier: "P1" },
  { code: "SEASONINGS", category: "FOOD", nameEn: "Seasonings", nameVi: "Gia vị", unit: "set", tier: "P2", aliases: ["Seasoning set", "Bộ gia vị"] },
  { code: "BOTTLED_WATER", category: "WATER", nameEn: "Drinking water", nameVi: "Nước uống", unit: "box", tier: "P1", aliases: ["Drinking Water", "Water", "Nước uống đóng chai"] },
  { code: "WATER_TREATMENT_KIT", category: "WATER", nameEn: "Water treatment kit", nameVi: "Bộ xử lý nước", unit: "set", tier: "P1" },
  { code: "HYGIENE_KIT", category: "HYGIENE", nameEn: "Hygiene kit", nameVi: "Bộ vệ sinh cá nhân", unit: "kit", tier: "P2", aliases: ["Hygiene"] },
  { code: "WOMENS_HYGIENE_KIT", category: "HYGIENE", nameEn: "Women's hygiene kit", nameVi: "Bộ vệ sinh phụ nữ", unit: "set", tier: "P2" },
  { code: "CLOTHING_SET", category: "CLOTHING", nameEn: "Clothing set (new)", nameVi: "Bộ quần áo (mới)", unit: "set", tier: "P2" },
  { code: "RAINCOAT", category: "CLOTHING", nameEn: "Raincoat", nameVi: "Áo mưa", unit: "piece", tier: "P1" },
  { code: "WARM_CLOTHING_SET", category: "CLOTHING", nameEn: "Warm clothing set", nameVi: "Bộ quần áo ấm", unit: "set", tier: "P2" },
  { code: "FOOTWEAR", category: "CLOTHING", nameEn: "Footwear", nameVi: "Giày dép", unit: "pair", tier: "P2" },
  { code: "BEDDING_KIT", category: "BEDDING_HOUSEHOLD", nameEn: "Bedding kit", nameVi: "Bộ chăn, chiếu, màn", unit: "set", tier: "P1" },
  { code: "HOUSEHOLD_KIT", category: "BEDDING_HOUSEHOLD", nameEn: "Household supplies kit", nameVi: "Bộ đồ dùng sinh hoạt", unit: "set", tier: "P3" },
  { code: "FIRST_AID_KIT", category: "MEDICAL", nameEn: "First aid kit", nameVi: "Bộ sơ cứu", unit: "kit", tier: "P1" },
  { code: "FACE_MASKS", category: "MEDICAL", nameEn: "Face masks", nameVi: "Khẩu trang", unit: "box", tier: "P2" },
  { code: "BABY_CARE_KIT", category: "CHILDREN", nameEn: "Baby care kit", nameVi: "Bộ chăm sóc trẻ em", unit: "set", tier: "P2" },
  { code: "INFANT_FORMULA", category: "CHILDREN", nameEn: "Infant formula (age-appropriate)", nameVi: "Sữa công thức (đúng độ tuổi)", unit: "can", tier: "P1" },
  { code: "ELDERLY_CARE_KIT", category: "ELDERLY", nameEn: "Elderly care kit", nameVi: "Bộ chăm sóc người cao tuổi", unit: "set", tier: "P2" },
  { code: "SOFT_FOOD", category: "ELDERLY", nameEn: "Soft food", nameVi: "Thực phẩm mềm", unit: "box", tier: "P2" },
  { code: "EMERGENCY_LIGHTING_KIT", category: "ESSENTIAL_EQUIPMENT", nameEn: "Emergency lighting kit", nameVi: "Bộ đèn pin, pin và còi cứu hộ", unit: "set", tier: "P1" },
  { code: "POWER_BANK", category: "ESSENTIAL_EQUIPMENT", nameEn: "Power bank", nameVi: "Sạc dự phòng", unit: "piece", tier: "P2" },
  { code: "SOLAR_LAMP", category: "ESSENTIAL_EQUIPMENT", nameEn: "Solar lamp", nameVi: "Đèn năng lượng mặt trời", unit: "piece", tier: "P2" },
  { code: "LIFE_JACKET", category: "DISASTER_PREP", nameEn: "Life jacket", nameVi: "Áo phao", unit: "piece", tier: "P1" },
  { code: "EMERGENCY_SHELTER_KIT", category: "DISASTER_PREP", nameEn: "Emergency shelter kit", nameVi: "Bộ vật tư che chắn", unit: "set", tier: "P1" },
  { code: "PROTECTIVE_EQUIPMENT_KIT", category: "DISASTER_PREP", nameEn: "Protective equipment kit", nameVi: "Bộ đồ bảo hộ", unit: "set", tier: "P2" },
  { code: "COOKING_KIT", category: "COOKING", nameEn: "Cooking utensils kit", nameVi: "Bộ dụng cụ nấu ăn", unit: "set", tier: "P3" },
  { code: "MINI_GAS_STOVE", category: "COOKING", nameEn: "Mini gas stove", nameVi: "Bếp gas mini", unit: "piece", tier: "P3" },
  { code: "SCHOOL_SUPPLIES_COMBO", category: "EDUCATION", nameEn: "School supplies combo", nameVi: "Combo đồ dùng học tập", unit: "combo", tier: "P3", aliases: ["School supplies", "Đồ dùng học tập", "Combo học tập"] },
];

export const UNIT_LABELS: Record<string, { en: string; vi: string }> = {
  kg: { en: "kg", vi: "kg" },
  combo: { en: "combo", vi: "combo" },
  bag: { en: "bag", vi: "bao" },
  pack: { en: "pack", vi: "gói" },
  box: { en: "box", vi: "thùng" },
  can: { en: "can", vi: "lon" },
  bottle: { en: "bottle", vi: "chai" },
  jug: { en: "jug", vi: "bình" },
  piece: { en: "piece", vi: "cái" },
  set: { en: "set", vi: "bộ" },
  kit: { en: "kit", vi: "bộ" },
  roll: { en: "roll", vi: "cuộn" },
  pair: { en: "pair", vi: "đôi" },
};

export const RELIEF_TIER_LABELS: Record<ReliefTier, { en: string; vi: string }> = {
  P1: { en: "Urgent · first 24–72 h", vi: "Khẩn cấp · 24–72 giờ đầu" },
  P2: { en: "Essential · following days", vi: "Thiết yếu · những ngày tiếp theo" },
  P3: { en: "Recovery", vi: "Phục hồi" },
};

function normalizedName(name: string): string {
  return name.trim().toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replaceAll("đ", "d");
}

const itemsByName = new Map<string, CatalogItem>();
for (const entry of ITEM_CATALOG) {
  for (const name of [entry.nameEn, entry.nameVi, ...(entry.aliases ?? [])]) {
    itemsByName.set(normalizedName(name), entry);
  }
}

export function findCatalogItem(name: string): CatalogItem | undefined {
  return itemsByName.get(normalizedName(name));
}

export function itemLabel(name: string, language: "en" | "vi"): string {
  if (language === "en") return name;
  return findCatalogItem(name)?.nameVi ?? name;
}

export function unitLabel(unit: string, language: "en" | "vi"): string {
  if (language === "en") return unit;
  const code = unit.trim().toLowerCase();
  // Historical API fixtures use PACKAGE and uppercase unit codes.
  const label = Object.hasOwn(UNIT_LABELS, code === "package" ? "pack" : code)
    ? UNIT_LABELS[code === "package" ? "pack" : code] : undefined;
  return label?.vi ?? unit;
}
