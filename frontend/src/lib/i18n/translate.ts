import { vi } from "./vi";
import { codeLabel } from "./labels";

export type Language = "en" | "vi";
export type TranslationVars = Record<string, string | number>;

/**
 * The English sentence is the key. Vietnamese falls back to English when a
 * sentence has no entry, so a missing translation never breaks a screen.
 */
export function translate(language: Language, text: string, vars?: TranslationVars): string {
  const template = language === "vi" ? vi[text] ?? text : text;
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (match, name: string) => name in vars ? String(vars[name]) : match);
}

/** API codes such as IN_PROGRESS or ORG_ADMIN are shown as readable labels in either language. */
export function statusLabel(language: Language, code: string): string {
  const label = codeLabel(code);
  return language === "vi" ? vi[label] ?? label : label;
}
