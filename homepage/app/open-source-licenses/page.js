import { headers } from "next/headers";
import { landingContent, openSourceContent } from "../content";
import { detectLocaleFromLanguage } from "../locale";

export const metadata = {
  title: "PeerSend Open Source Licenses",
  description: "Open source licenses used by PeerSend",
};

export default async function OpenSourceLicensesPage() {
  const requestHeaders = await headers();
  const locale = detectLocaleFromLanguage(requestHeaders.get("accept-language"));
  const content = openSourceContent[locale];
  const footer = landingContent[locale].footer;

  return (
    <main className="docs-shell">
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

      <section className="docs-hero">
        <p className="section-tag">{content.tag}</p>
        <h1>{content.title}</h1>
        <p>{content.intro}</p>
      </section>

      {content.sections.map((section) => (
        <section className="docs-section" key={section.title}>
          <h2>{section.title}</h2>
          <p>{section.description}</p>
          <div className="oss-list">
            {section.items.map((item) => (
              <article className="oss-item" key={`${section.title}-${item.name}`}>
                <div className="oss-main">
                  <strong>{item.name}</strong>
                  {item.href ? (
                    <a className="oss-link" href={item.href} target="_blank" rel="noreferrer">
                      {item.href}
                    </a>
                  ) : null}
                </div>
                <div className="oss-meta">
                  <span>{item.version}</span>
                  <span>{item.license}</span>
                </div>
              </article>
            ))}
          </div>
          {section.notes?.length ? (
            <div className="oss-notes">
              {section.notes.map((note) => (
                <p key={note}>{note}</p>
              ))}
            </div>
          ) : null}
        </section>
      ))}

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
