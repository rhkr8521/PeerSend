"use client";

import { useEffect, useState } from "react";
import { landingContent } from "./content";
import { detectLocaleFromNavigator } from "./locale";

const transferScenes = [
  {
    left: { kind: "pc" },
    right: { kind: "phone" },
  },
  {
    left: { kind: "pc" },
    right: { kind: "pc" },
  },
  {
    left: { kind: "phone" },
    right: { kind: "phone" },
  },
];

function useRevealAnimation() {
  useEffect(() => {
    const nodes = document.querySelectorAll("[data-reveal]");
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.18, rootMargin: "0px 0px -10%" }
    );

    nodes.forEach((node, index) => {
      node.style.setProperty("--reveal-delay", `${index * 70}ms`);
      observer.observe(node);
    });

    return () => observer.disconnect();
  }, []);
}

function TransferDevice({ device, locale }) {
  const labels = {
    pc: "PC",
    phone: "Phone",
  };
  const meta = {
    pc: "Windows / Mac / Linux",
    phone: locale === "ko" ? "Android / iOS" : "Android / iOS",
  };

  if (device.kind === "pc") {
    return (
      <div className="transfer-device transfer-device-pc">
        <div className="desktop-shell">
          <div className="desktop-screen">
            <div className="transfer-device-copy">
              <span className="transfer-device-label">{labels.pc}</span>
              <strong>PeerSend</strong>
            </div>
            <p className="transfer-device-meta">{meta.pc}</p>
          </div>
          <div className="desktop-stand" />
          <div className="desktop-base" />
        </div>
      </div>
    );
  }

  return (
    <div className="transfer-device transfer-device-phone">
      <div className="handset-shell">
        <div className="handset-notch" />
        <div className="handset-screen">
          <div className="transfer-device-copy">
            <span className="transfer-device-label">{labels.phone}</span>
            <strong>PeerSend</strong>
          </div>
          <p className="transfer-device-meta">{meta.phone}</p>
        </div>
      </div>
    </div>
  );
}

function TransferScene({ sceneIndex, locale }) {
  const scene = transferScenes[sceneIndex % transferScenes.length];

  return (
    <div className="transfer-scene" aria-live="polite">
      <TransferDevice device={scene.left} locale={locale} />
      <div className="transfer-rail">
        <div className="transfer-line" />
        <div className="transfer-file">
          <span className="transfer-file-fold" />
        </div>
      </div>
      <TransferDevice device={scene.right} locale={locale} />
    </div>
  );
}

function FlexibleScene() {
  return (
    <div className="flexible-scene" aria-hidden="true">
      <div className="flexible-stage">
        <div className="flexible-device flexible-device-pc">
          <div className="desktop-shell flexible-desktop">
            <div className="desktop-screen flexible-screen">
              <span className="transfer-device-label">PC</span>
              <div className="flexible-file-card flexible-file-card-large">
                <span className="flexible-file-type">MOV</span>
                <strong>Original file</strong>
              </div>
              <div className="flexible-meter">
                <span />
                <span />
              </div>
            </div>
            <div className="desktop-stand" />
            <div className="desktop-base" />
          </div>
        </div>

        <div className="flexible-flow-lane">
          <div className="flexible-lane-line" />
          <span className="lane-chip lane-chip-primary">FILE</span>
          <span className="lane-chip lane-chip-secondary">ZIP</span>
          <span className="lane-glow lane-glow-a" />
          <span className="lane-glow lane-glow-b" />
        </div>

        <div className="flexible-device flexible-device-phone">
          <div className="handset-shell flexible-handset">
            <div className="handset-notch" />
            <div className="handset-screen flexible-screen">
              <span className="transfer-device-label">Phone</span>
              <div className="flexible-stack">
                <span className="flexible-stack-item">IMG_2048</span>
                <span className="flexible-stack-item">DOC_share</span>
                <span className="flexible-stack-item">ZIP bundle</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function RemoteScene() {
  return (
    <div className="remote-scene" aria-hidden="true">
      <div className="remote-glow remote-glow-left" />
      <div className="remote-glow remote-glow-right" />

      <div className="remote-device remote-device-left">
        <div className="desktop-shell remote-desktop">
          <div className="desktop-screen remote-screen">
            <div className="transfer-device-copy">
              <span className="transfer-device-label">PC</span>
              <strong>PeerSend</strong>
            </div>
          </div>
          <div className="desktop-stand" />
          <div className="desktop-base" />
        </div>
      </div>

      <div className="remote-bridge">
        <div className="remote-bridge-arc" />
        <div className="remote-bridge-arc remote-bridge-arc-soft" />
        <span className="remote-bridge-label">TUNNEL</span>
        <span className="remote-packet remote-packet-a">FILE</span>
        <span className="remote-packet remote-packet-b">ZIP</span>
        <span className="remote-node remote-node-a" />
        <span className="remote-node remote-node-b" />
        <span className="remote-node remote-node-c" />
      </div>

      <div className="remote-device remote-device-right">
        <div className="handset-shell remote-handset">
          <div className="handset-notch" />
          <div className="handset-screen remote-screen">
            <div className="transfer-device-copy">
              <span className="transfer-device-label">Phone</span>
              <strong>PeerSend</strong>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function StoreIcon({ type }) {
  if (type === "play") {
    return (
      <svg className="store-icon" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M3.8 3.1c-.3.3-.4.8-.4 1.5v14.8c0 .7.2 1.2.5 1.5l.1.1L13.3 12 4 3z" fill="#00D3FF" />
        <path d="M13.3 12 17 8.3l-4.7-2.7L4 3l9.3 9Z" fill="#46F081" />
        <path d="m13.3 12-9.2 9 .1.1c.3.2.8.2 1.4-.1l10.9-6.2L13.3 12Z" fill="#FFD84A" />
        <path d="m20.1 10.4-3.1-1.8-3.6 3.4 3.7 3.6 3-1.7c.9-.5.9-1.3.9-1.9 0-.6 0-1.2-.9-1.6Z" fill="#FF6B5E" />
      </svg>
    );
  }

  if (type === "apple") {
    return (
      <svg className="store-icon" viewBox="0 0 24 24" aria-hidden="true">
        <rect x="2.5" y="2.5" width="19" height="19" rx="5" fill="#0A84FF" />
        <path d="M9.4 8.2h1.6l4.2 7.6h-1.6L9.4 8.2Zm3.6 0h1.6L10.4 16H8.8L13 8.2Zm-5.7 6.1h9.4v1.5H7.3v-1.5Z" fill="#fff" />
      </svg>
    );
  }

  return null;
}

function ExperienceBand({ band, sceneIndex, locale }) {
  if (band.id === "direct") {
    return (
      <article className={`experience-band experience-flow ${band.tone}`} data-reveal>
        <div className="experience-copy">
          <p className="band-kicker">{band.kicker}</p>
          <h3>{band.title}</h3>
          <p>{band.body}</p>
        </div>
        <TransferScene sceneIndex={sceneIndex} locale={locale} />
      </article>
    );
  }

  if (band.id === "flexible") {
    return (
      <article className={`experience-band experience-spectrum ${band.tone}`} data-reveal>
        <FlexibleScene />
        <div className="experience-copy">
          <p className="band-kicker">{band.kicker}</p>
          <h3>{band.title}</h3>
          <p>{band.body}</p>
          {band.points.length > 0 ? (
            <div className="spectrum-grid">
              {band.points.map((point) => (
                <div className="spectrum-item" key={point}>
                  <strong>{point}</strong>
                </div>
              ))}
            </div>
          ) : null}
        </div>
      </article>
    );
  }

  if (band.id === "tunnel") {
    return (
      <article className={`experience-band experience-flow ${band.tone}`} data-reveal>
        <div className="experience-copy">
          <p className="band-kicker">{band.kicker}</p>
          <h3>{band.title}</h3>
          <p>{band.body}</p>
        </div>
        <RemoteScene />
      </article>
    );
  }

  return null;
}

function MobileExperienceBand({ band, sceneIndex, locale }) {
  const showVisual = band.id === "direct";

  return (
    <article className={`mobile-experience-card ${band.tone}`} data-reveal>
      <div className="mobile-experience-copy">
        <p className="band-kicker">{band.kicker}</p>
        <h3>{band.title}</h3>
        <p>{band.body}</p>
      </div>
      {showVisual ? (
        <div className="mobile-experience-visual">
          <TransferScene sceneIndex={sceneIndex} locale={locale} />
        </div>
      ) : null}
    </article>
  );
}

export default function LandingClient({ initialLocale = "en" }) {
  const [locale, setLocale] = useState(initialLocale === "ko" ? "ko" : "en");
  const [sceneIndex, setSceneIndex] = useState(0);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const content = landingContent[locale];
  const platformCards = content.platforms;
  const featureBands = content.features.map((band, index) => ({
    ...band,
    tone: ["tone-coral", "tone-mint", "tone-ink"][index],
    points: [],
  }));
  const downloads = content.downloads.items;
  useRevealAnimation();

  useEffect(() => {
    setLocale(detectLocaleFromNavigator());
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => {
      setSceneIndex((prev) => (prev + 1) % transferScenes.length);
    }, 2600);

    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    const closeMenu = () => setMobileMenuOpen(false);
    window.addEventListener("resize", closeMenu);
    return () => window.removeEventListener("resize", closeMenu);
  }, []);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    if (mobileMenuOpen) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = previousOverflow || "";
    }

    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [mobileMenuOpen]);

  return (
    <main className="landing-shell">
      <div className="mesh mesh-one" />
      <div className="mesh mesh-two" />
      <div className="mesh mesh-three" />

      <header className="site-topbar desktop-only">
        <div className="brand-wrap">
          <a className="brand-wordmark" href="#story">
            PeerSend
          </a>
        </div>
        <nav className="site-nav">
          <a href="#story">{content.nav.story}</a>
          <a href="#experience">{content.nav.experience}</a>
          <a href="#download">{content.nav.download}</a>
        </nav>
        <a className="topbar-action" href="/tunnel-server">
          {content.nav.tunnelServer}
        </a>
      </header>

      <header className="mobile-topbar mobile-only">
        <a className="mobile-brand" href="#story">
          PeerSend
        </a>
        <button
          type="button"
          className={`mobile-menu-toggle ${mobileMenuOpen ? "is-open" : ""}`}
          aria-expanded={mobileMenuOpen}
          aria-controls="mobile-site-nav"
          aria-label={content.nav.menuOpen}
          onClick={() => setMobileMenuOpen((prev) => !prev)}
        >
          <span />
          <span />
          <span />
        </button>
      </header>

      <div
        className={`mobile-nav-shell mobile-only ${mobileMenuOpen ? "is-open" : ""}`}
        onClick={() => setMobileMenuOpen(false)}
      >
        <nav
          id="mobile-site-nav"
          className={`mobile-nav ${mobileMenuOpen ? "is-open" : ""}`}
          aria-label={locale === "ko" ? "모바일 탐색" : "Mobile navigation"}
          onClick={(event) => event.stopPropagation()}
        >
          <div className="mobile-nav-head">
            <strong>PeerSend</strong>
            <button type="button" className="mobile-nav-close" onClick={() => setMobileMenuOpen(false)}>
              {content.nav.close}
            </button>
          </div>
          <a href="#story" onClick={() => setMobileMenuOpen(false)}>
            {content.nav.story}
          </a>
          <a href="#experience" onClick={() => setMobileMenuOpen(false)}>
            {content.nav.experience}
          </a>
          <a href="#download" onClick={() => setMobileMenuOpen(false)}>
            {content.nav.download}
          </a>
          <a href="/tunnel-server" onClick={() => setMobileMenuOpen(false)}>
            {content.nav.tunnelServer}
          </a>
        </nav>
      </div>

      <section id="story">
        <section className="hero-section desktop-only">
          <div className="hero-copy" data-reveal>
            <p className="section-tag">{content.hero.tag}</p>
            <h1>
              {content.hero.titleLines[0]}
              <br />
              {content.hero.titleLines[1]}
            </h1>
            <p className="hero-description">{content.hero.description}</p>
            <div className="hero-actions">
              <a className="action-primary" href="#download">
                {content.hero.start}
              </a>
            </div>
          </div>

          <div className="hero-stage" data-reveal>
            <div className="device-mock phone-mock phone-ios">
              <div className="phone-frame">
                <div className="phone-camera" />
                <div className="phone-screen phone-screen-ios">
                  <span className="phone-badge">iPhone</span>
                  <strong>PeerSend</strong>
                  <p>{content.hero.signals.iosStatus}</p>
                </div>
              </div>
            </div>
            <div className="signal-card signal-card-main">
              <span className="signal-chip">LAN</span>
              <strong>{content.hero.signals.lanTitle}</strong>
              <p>{content.hero.signals.lanBody}</p>
            </div>
            <div className="signal-card signal-card-side">
              <span className="signal-chip warm">Tunnel</span>
              <strong>{content.hero.signals.tunnelTitle}</strong>
              <p>{content.hero.signals.tunnelBody}</p>
            </div>
            <div className="device-mock phone-mock phone-android">
              <div className="phone-frame android-frame">
                <div className="phone-screen phone-screen-android">
                  <span className="phone-badge android-badge">Android</span>
                  <strong>PeerSend</strong>
                  <p>{content.hero.signals.androidStatus}</p>
                </div>
              </div>
            </div>
            <div className="signal-console">
              <div className="console-head">
                <span />
                <span />
                <span />
              </div>
              <div className="console-body">
                <p>{content.hero.console.tag}</p>
                <h2>{content.hero.console.title}</h2>
                <div className="console-bars">
                  <span />
                  <span />
                  <span />
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="mobile-hero mobile-only">
          <div className="mobile-hero-copy" data-reveal>
            <p className="section-tag">{content.hero.tag}</p>
            <h1>{content.hero.mobileTitle}</h1>
            <p>{content.hero.mobileDescription}</p>
            <a className="action-primary mobile-hero-action" href="#download">
              {content.hero.start}
            </a>
          </div>
          <div className="mobile-hero-stage" data-reveal>
            <TransferScene sceneIndex={sceneIndex} locale={locale} />
          </div>
        </section>
      </section>

      <section className="platform-grid desktop-only">
        {platformCards.map((card) => (
          <article className="platform-card" data-reveal key={card.label}>
            <span className="platform-label">{card.label}</span>
            <h3>{card.title}</h3>
            <p>{card.body}</p>
            {card.note ? <small className="platform-note">{card.note}</small> : null}
          </article>
        ))}
      </section>

      <section className="mobile-platform-list mobile-only">
        {platformCards.map((card) => (
          <article className="mobile-platform-card" data-reveal key={card.label}>
            <span className="platform-label">{card.label}</span>
            <h3>{card.title}</h3>
            <p>{card.body}</p>
            {card.note ? <small className="platform-note">{card.note}</small> : null}
          </article>
        ))}
      </section>

      <section className="marquee-band" data-reveal>
        <div className="marquee-track">
          <div className="marquee-group">
            <span>Windows</span>
            <span>macOS</span>
            <span>Linux</span>
            <span>Android</span>
            <span>iPhone</span>
            <span>iPad</span>
            <span>LAN</span>
            <span>Tunnel</span>
            <span>PeerSend</span>
          </div>
          <div className="marquee-group" aria-hidden="true">
            <span>Windows</span>
            <span>macOS</span>
            <span>Linux</span>
            <span>Android</span>
            <span>iPhone</span>
            <span>iPad</span>
            <span>LAN</span>
            <span>Tunnel</span>
            <span>PeerSend</span>
          </div>
        </div>
      </section>

      <section className="experience-stack desktop-only" id="experience">
        {featureBands.map((band) => (
          <ExperienceBand band={band} key={band.title} sceneIndex={sceneIndex} locale={locale} />
        ))}
      </section>

      <section className="mobile-experience-stack mobile-only" id="experience">
        {featureBands.map((band) => (
          <MobileExperienceBand band={band} key={band.title} sceneIndex={sceneIndex} locale={locale} />
        ))}
      </section>

      <section id="download">
        <section className="download-section desktop-only" data-reveal>
          <div className="download-copy">
            <p className="section-tag">{content.downloads.tag}</p>
            <h2>{content.downloads.title}</h2>
            <p>{content.downloads.body}</p>
          </div>
          <div className="download-grid">
            {downloads.map((item) => (
              <a className="download-tile" href={item.href} key={item.platform}>
                <span>{item.platform}</span>
                <strong>{item.detail}</strong>
                <em>
                  {item.store ? <StoreIcon type={item.store} /> : null}
                  {item.platform === "PC" ? content.downloads.ctaOpen : content.downloads.ctaDownload}
                </em>
              </a>
            ))}
          </div>
        </section>

        <section className="mobile-download-section mobile-only" data-reveal>
          <div className="download-copy">
            <p className="section-tag">{content.downloads.tag}</p>
            <h2>{content.downloads.mobileTitle}</h2>
            <p>{content.downloads.mobileBody}</p>
          </div>
          <div className="mobile-download-list">
            {downloads.map((item) => (
              <a className="download-tile mobile-download-tile" href={item.href} key={item.platform}>
                <span>{item.platform}</span>
                <strong>{item.detail}</strong>
                <em>
                  {item.store ? <StoreIcon type={item.store} /> : null}
                  {item.platform === "PC" ? content.downloads.ctaOpen : content.downloads.ctaDownload}
                </em>
              </a>
            ))}
          </div>
        </section>
      </section>

      <footer className="site-footer">
        <div className="footer-copy">
          <strong>{content.footer.brand}</strong>
          <p>{content.footer.body}</p>
          <div className="footer-links">
            <a className="footer-link" href="/privacy">
              {content.footer.privacy}
            </a>
            <a className="footer-link" href="/terms">
              {content.footer.terms}
            </a>
          </div>
        </div>
        <span>© {new Date().getFullYear()} rhkr8521. {content.footer.copyright}</span>
      </footer>
    </main>
  );
}
