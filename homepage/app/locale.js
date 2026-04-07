export function normalizeLocale(value) {
  const normalized = String(value || "").trim().toLowerCase();
  return normalized.startsWith("ko") ? "ko" : normalized ? "en" : "";
}

function extractPrimaryLanguage(value) {
  if (!value) {
    return "";
  }

  if (Array.isArray(value)) {
    const primary = value.find((candidate) => typeof candidate === "string" && candidate.trim());
    return String(primary || "").trim();
  }

  return String(value)
    .split(",")[0]
    .split(";")[0]
    .trim();
}

export function detectLocale(preferredValue, fallbackValue = "") {
  return normalizeLocale(preferredValue) || normalizeLocale(extractPrimaryLanguage(fallbackValue)) || "en";
}

export function detectLocaleFromLanguage(value) {
  return detectLocale("", value);
}

export function withLocalePath(path, locale) {
  const targetLocale = normalizeLocale(locale) || "en";
  const [base, hash = ""] = String(path || "").split("#");
  const separator = base.includes("?") ? "&" : "?";
  return `${base}${separator}lang=${targetLocale}${hash ? `#${hash}` : ""}`;
}

export function detectLocaleFromNavigator() {
  if (typeof window === "undefined" || !window.navigator) {
    return "en";
  }

  try {
    const url = new URL(window.location.href);
    const queryLocale = url.searchParams.get("lang");
    if (queryLocale) {
      return detectLocale(queryLocale);
    }
  } catch {
    // ignore URL parsing failures
  }

  const primary =
    Array.isArray(window.navigator.languages) && window.navigator.languages.length > 0
      ? window.navigator.languages[0]
      : window.navigator.language;

  return detectLocale("", primary);
}
