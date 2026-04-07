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

export function detectLocaleFromLanguage(value) {
  const primary = extractPrimaryLanguage(value).toLowerCase();
  return primary.startsWith("ko") ? "ko" : "en";
}

export function detectLocaleFromNavigator() {
  if (typeof window === "undefined" || !window.navigator) {
    return "en";
  }

  const primary =
    Array.isArray(window.navigator.languages) && window.navigator.languages.length > 0
      ? window.navigator.languages[0]
      : window.navigator.language;

  return detectLocaleFromLanguage(primary);
}
