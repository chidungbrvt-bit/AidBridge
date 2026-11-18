import { vi } from "./vi";

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

/** Status codes such as IN_PROGRESS keep their code in data, only the label is translated. */
export function statusLabel(language: Language, status: string): string {
  const label = status.replaceAll("_", " ");
  return language === "vi" ? vi[label] ?? label : label;
}
