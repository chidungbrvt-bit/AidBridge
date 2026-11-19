"use client";

import { useI18n } from "../i18n-provider";
import { findCatalogItem, ITEM_CATALOG, ITEM_CATEGORIES, RELIEF_TIER_LABELS } from "../../lib/item-catalog";
import type { NeedInput } from "../../lib/types";
import { FieldError, type FieldIssue } from "./shared";

export type NeedRow = NeedInput & { rowKey: string; customName?: boolean };

export function duplicateNeedIssues(needs: readonly NeedInput[]): FieldIssue[] {
  const seen = new Set<string>();
  const issues: FieldIssue[] = [];
  needs.forEach((need, index) => {
    const identity = findCatalogItem(need.name)?.code ?? need.name.trim().toLowerCase();
    if (identity && seen.has(identity)) issues.push({ field: `needs[${index}].name`, code: "DUPLICATE_NEED" });
    seen.add(identity);
  });
  return issues;
}

export function NeedFields({ need, index, issues, onChange }: {
  need: NeedRow;
  index: number;
  issues: FieldIssue[];
  onChange: (patch: Partial<NeedRow>) => void;
}) {
  const { t, language, unit } = useI18n();
  const entry = need.customName ? undefined : findCatalogItem(need.name);
  const custom = need.customName || (!!need.name && !entry);
  const field = (key: string) => `needs[${index}].${key}`;
  const invalid = (key: string) => issues.some(issue => issue.field === field(key));
  const description = (key: string) => invalid(key) ? `error-${field(key)}` : undefined;

  return <>
    <div className="field">
      <label htmlFor={`need-name-${need.rowKey}`}>{t("Item {n} name", { n: index + 1 })}</label>
      <select id={`need-name-${need.rowKey}`} name={field("name")}
        aria-label={`Item ${index + 1} name`} value={custom ? "other" : entry?.nameEn ?? ""}
        required aria-invalid={invalid("name")} aria-describedby={description("name")}
        onChange={event => {
          if (event.target.value === "other") {
            onChange({ customName: true, name: entry ? "" : need.name });
            return;
          }
          const selected = findCatalogItem(event.target.value);
          onChange({ customName: false, name: selected?.nameEn ?? "", unit: selected?.unit ?? "" });
        }}>
        <option value="" disabled>{t("Select an item")}</option>
        {ITEM_CATEGORIES.map(category => <optgroup key={category.code} label={language === "vi" ? category.nameVi : category.nameEn}>
          {ITEM_CATALOG.filter(item => item.category === category.code).map(item => <option key={item.code} value={item.nameEn}>
            {language === "vi" ? item.nameVi : item.nameEn}
          </option>)}
        </optgroup>)}
        <option value="other">{t("Other (type a name)")}</option>
      </select>
      {custom && <input name={field("otherName")} aria-label={t("Item {n} other name", { n: index + 1 })}
        value={need.name} onChange={event => onChange({ name: event.target.value })} required maxLength={120}
        aria-invalid={invalid("name")} aria-describedby={description("name")} />}
      <FieldError name={field("name")} issues={issues} />
      {entry && <span className="badge">{RELIEF_TIER_LABELS[entry.tier][language]}</span>}
    </div>
    <label className="field"><span>{t("Unit")}</span>
      <input name={field("unit")} aria-label={`Item ${index + 1} unit`} value={entry ? unit(need.unit) : need.unit}
        readOnly={!!entry} onChange={event => onChange({ unit: event.target.value })}
        required maxLength={40} aria-invalid={invalid("unit")} aria-describedby={description("unit")} />
      <FieldError name={field("unit")} issues={issues} />
    </label>
    <label className="field"><span>{t("Quantity")}</span>
      <input type="number" name={field("quantity")} aria-label={`Item ${index + 1} quantity`}
        value={Number.isNaN(need.quantity) ? "" : need.quantity} onChange={event => onChange({ quantity: event.target.valueAsNumber })}
        required min={1} max={1000000} step={1} aria-invalid={invalid("quantity")} aria-describedby={description("quantity")} />
      <FieldError name={field("quantity")} issues={issues} />
    </label>
  </>;
}
