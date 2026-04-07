import { headers } from "next/headers";
import { landingContent, privacyContent } from "../content";
import { detectLocaleFromLanguage } from "../locale";

export const metadata = {
  title: "PeerSend Privacy Policy",
  description: "PeerSend Privacy Policy",
};

export default async function PrivacyPage() {
  const requestHeaders = await headers();
  const locale = detectLocaleFromLanguage(requestHeaders.get("accept-language"));
  const content = privacyContent[locale];
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

      <section className="docs-section">
        <p>{content.preface}</p>
      </section>

      {content.sections.map((section) => (
        <section className="docs-section" key={section.title}>
          <h2>{section.title}</h2>
          {section.paragraphs?.map((paragraph, index) => {
            const isLast = index === section.paragraphs.length - 1 && section.emphasisLast;
            return isLast ? (
              <p key={paragraph}>
                <strong>{paragraph}</strong>
              </p>
            ) : (
              <p key={paragraph}>{paragraph}</p>
            );
          })}
          {section.list ? (
            <ul>
              {section.list.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
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
          </div>
        </div>
        <span>© {new Date().getFullYear()} rhkr8521. {footer.copyright}</span>
      </footer>
    </main>
  );
}
