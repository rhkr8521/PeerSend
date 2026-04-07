import { headers } from "next/headers";
import { landingContent, noticeContent } from "../content";
import { detectLocale, withLocalePath } from "../locale";
import SiteFooter from "../components/SiteFooter";

export const metadata = {
  title: "PeerSend Notice",
  description: "PeerSend service notices and updates",
};

export default async function NoticePage({ searchParams }) {
  const requestHeaders = await headers();
  const params = await searchParams;
  const locale = detectLocale(params?.lang, requestHeaders.get("accept-language"));
  const content = noticeContent[locale];
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
        <div className="notice-board">
          <div className="notice-table-head desktop-only">
            <span>{content.columns.number}</span>
            <span>{content.columns.title}</span>
            <span>{content.columns.date}</span>
          </div>

          {content.posts.length > 0 ? (
            <div className="notice-table-body">
              {content.posts.map((post, index) => (
                <article className="notice-row" key={`${post.date}-${post.title}`}>
                  <span className="notice-col notice-col-number">{content.posts.length - index}</span>
                  <div className="notice-col notice-col-title">
                    <strong>{post.title}</strong>
                    <p className="mobile-only">{post.date}</p>
                  </div>
                  <span className="notice-col notice-col-date desktop-only">{post.date}</span>
                </article>
              ))}
            </div>
          ) : (
            <div className="notice-empty">{content.empty}</div>
          )}

          <div className="notice-pagination" aria-label="Notice pagination">
            <button type="button" className="notice-page-button" disabled>
              {content.pagination.prev}
            </button>
            <button type="button" className="notice-page-button is-active" aria-current="page">
              1
            </button>
            <button type="button" className="notice-page-button" disabled>
              {content.pagination.next}
            </button>
          </div>
        </div>
      </section>

      <SiteFooter footer={footer} locale={locale} />
    </main>
  );
}
