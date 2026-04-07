import { headers } from "next/headers";
import { landingContent, privacyContent } from "../content";
import { detectLocale, withLocalePath } from "../locale";
import SiteFooter from "../components/SiteFooter";

export const metadata = {
  title: "PeerSend Privacy Policy",
  description: "PeerSend Privacy Policy",
};

export default async function PrivacyPage({ searchParams }) {
  const requestHeaders = await headers();
  const params = await searchParams;
  const locale = detectLocale(params?.lang, requestHeaders.get("accept-language"));
  const content = privacyContent[locale];
  const footer = landingContent[locale].footer;

  return (
    <main className="docs-shell">
      <header className="docs-topbar desktop-only">
        <a className="docs-brand" href={withLocalePath("/", locale)}>
          PeerSend
        </a>
        <nav className="docs-nav">
          <a href={withLocalePath("/", locale)}>{content.home}</a>
        </nav>
      </header>

      <header className="docs-mobile-topbar mobile-only">
        <a className="docs-mobile-brand" href={withLocalePath("/", locale)}>
          PeerSend
        </a>
        <a className="docs-mobile-home" href={withLocalePath("/", locale)}>
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

      <SiteFooter footer={footer} locale={locale} />
    </main>
  );
}
