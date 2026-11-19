"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { statusLabel, translate, type Language, type TranslationVars } from "../lib/i18n/translate";

import { itemLabel, unitLabel } from "../lib/item-catalog";

const STORAGE_KEY = "aidbridge.language";

type I18nValue = {
  language: Language;
  setLanguage: (language: Language) => void;
  t: (text: string, vars?: TranslationVars) => string;
  status: (code: string) => string;
  item: (name: string) => string;
  unit: (code: string) => string;
};

// Components rendered outside the provider (dev harnesses) keep English.
const english: I18nValue = {
  item: name => itemLabel(name, "en"), unit: code => unitLabel(code, "en"),
  language: "en", setLanguage: () => {},
  t: (text, vars) => translate("en", text, vars), status: code => statusLabel("en", code),
};
const I18nContext = createContext<I18nValue>(english);

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const [language, setCurrent] = useState<Language>("en");
  useEffect(() => {
    try { if (window.localStorage.getItem(STORAGE_KEY) === "vi") setCurrent("vi"); } catch { /* storage unavailable: stay in English */ }
  }, []);
  useEffect(() => { document.documentElement.lang = language; }, [language]);
  const setLanguage = useCallback((next: Language) => {
    setCurrent(next);
    // Only the language choice is stored, and only after the user picks one.
    try { window.localStorage.setItem(STORAGE_KEY, next); } catch { /* keep the choice for this tab only */ }
  }, []);
  const value = useMemo<I18nValue>(() => ({
    language, setLanguage,
    t: (text, vars) => translate(language, text, vars),
    status: code => statusLabel(language, code),
    item: name => itemLabel(name, language),
    unit: code => unitLabel(code, language),
  }), [language, setLanguage]);
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  return useContext(I18nContext);
}

export function LanguageToggle({ className = "" }: { className?: string }) {
  const { language, setLanguage } = useI18n();
  return <div className={`language-toggle ${className}`.trim()} role="group" aria-label="Language / Ngôn ngữ">
    <button type="button" aria-pressed={language === "en"} lang="en" onClick={() => setLanguage("en")}>EN</button>
    <button type="button" aria-pressed={language === "vi"} lang="vi" onClick={() => setLanguage("vi")}>VI</button>
  </div>;
}
