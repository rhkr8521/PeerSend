import { headers } from "next/headers";
import { landingContent, notFoundContent } from "./content";
import { detectLocaleFromLanguage } from "./locale";

export default async function NotFoundPage() {
  const requestHeaders = await headers();
  const locale = detectLocaleFromLanguage(requestHeaders.get("accept-language"));
  const content = notFoundContent[locale];
  const footer = landingContent[locale].footer;

  return (
    <main className="docs-shell not-found-shell">
      <header className="docs-topbar desktop-only">
        <a className="docs-brand" href="/">
          PeerSend
        </a>
        <nav className="docs-nav">
          <a href="/">{content.home}</a>
        </nav>
      </header>

      <header className="docs-mobile-topbar mobile-only">
        <a className="docs-mobile-brand" href="/">
          PeerSend
        </a>
        <a className="docs-mobile-home" href="/">
          {content.home}
        </a>
      </header>

      <section className="docs-hero not-found-hero">
        <p className="section-tag">{content.tag}</p>
        <h1>{content.title}</h1>
        <p>{content.intro}</p>
      </section>

      <footer className="site-footer docs-footer">
        <div className="footer-copy">
          <strong>{footer.brand}</strong>
          <p>{footer.body}</p>
          <div className="footer-links">
            <a className="footer-link" href="/privacy">
              {footer.privacy}
            </a>
            <a className="footer-link" href="/terms">
              {footer.terms}
            </a>
            <a className="footer-link" href="/open-source-licenses">
              {footer.openSource}
            </a>
          </div>
        </div>
        <span>© {new Date().getFullYear()} rhkr8521. {footer.copyright}</span>
      </footer>
    </main>
  );
}
