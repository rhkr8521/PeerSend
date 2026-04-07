"use client";

import { normalizeLocale, withLocalePath } from "../locale";

function LocaleButton({ label, targetLocale, currentLocale }) {
  const isActive = currentLocale === targetLocale;

  return (
    <button
      type="button"
      className={`footer-locale-button${isActive ? " is-active" : ""}`}
      onClick={() => {
        const nextLocale = normalizeLocale(targetLocale) || "en";
        window.location.href = withLocalePath(
          `${window.location.pathname}${window.location.search}${window.location.hash}`,
          nextLocale,
        );
      }}
      aria-pressed={isActive}
    >
      {label}
    </button>
  );
}

export default function SiteFooter({ footer, locale }) {
  const currentYear = new Date().getFullYear();
  const currentLocale = normalizeLocale(locale) || "en";
  const localeSwitcher = (
    <div className="footer-locale-switcher" aria-label="Language switcher">
      <LocaleButton label="English" targetLocale="en" currentLocale={currentLocale} />
      <LocaleButton label="한국어" targetLocale="ko" currentLocale={currentLocale} />
    </div>
  );

  return (
    <footer className="site-footer docs-footer">
      <div className="footer-copy">
        <div className="footer-brand-row">
          <strong>{footer.brand}</strong>
          <div className="mobile-only">{localeSwitcher}</div>
        </div>
        <p>{footer.body}</p>
        <div className="footer-links">
          <a className="footer-link" href={withLocalePath("/privacy", currentLocale)}>
            {footer.privacy}
          </a>
          <a className="footer-link" href={withLocalePath("/terms", currentLocale)}>
            {footer.terms}
          </a>
          <a className="footer-link" href={withLocalePath("/open-source-licenses", currentLocale)}>
            {footer.openSource}
          </a>
        </div>
      </div>
      <div className="footer-meta">
        <span className="footer-copyright">
          © {currentYear} rhkr8521. {footer.copyright}
        </span>
        <div className="desktop-only">{localeSwitcher}</div>
      </div>
    </footer>
  );
}
