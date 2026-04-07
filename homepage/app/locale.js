export function detectLocaleFromLanguage(value) {
  if (!value) {
    return "en";
  }

  return /(^|[,\s])ko(?:-|;|,|$)/i.test(value) ? "ko" : "en";
}

export function detectLocaleFromNavigator() {
  if (typeof window === "undefined" || !window.navigator) {
    return "en";
  }

  const candidates = Array.isArray(window.navigator.languages) && window.navigator.languages.length > 0
    ? window.navigator.languages
    : [window.navigator.language].filter(Boolean);

  return candidates.some((candidate) => /^ko\b/i.test(candidate)) ? "ko" : "en";
}
