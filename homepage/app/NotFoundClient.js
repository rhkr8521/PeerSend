"use client";

import { useEffect, useState } from "react";
import { landingContent, notFoundContent } from "./content";
import { detectLocaleFromNavigator, withLocalePath } from "./locale";
import SiteFooter from "./components/SiteFooter";

export default function NotFoundClient() {
  const [locale, setLocale] = useState("en");
  const content = notFoundContent[locale];
  const footer = landingContent[locale].footer;

  useEffect(() => {
    setLocale(detectLocaleFromNavigator());
  }, []);

  return (
    <main className="docs-shell not-found-shell">
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

      <section className="docs-hero not-found-hero">
        <p className="section-tag">{content.tag}</p>
        <h1>{content.title}</h1>
        <p>{content.intro}</p>
      </section>

      <SiteFooter footer={footer} locale={locale} />
    </main>
  );
}
