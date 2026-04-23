"use client";

import { useCallback, useEffect, useState } from "react";
import { SiGoogleplay, SiAppstore } from "react-icons/si";
import { landingContent } from "./content";
import { detectLocaleFromNavigator, withLocalePath } from "./locale";
import SiteFooter from "./components/SiteFooter";

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
        <div className="transfer-beam" />
        <div className="transfer-packet">
          <span className="transfer-packet-fold" />
        </div>
        <span className="ts-spark ts-spark-a" />
        <span className="ts-spark ts-spark-b" />
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
                <span className="flexible-meter-bar" />
                <span className="flexible-meter-bar" />
              </div>
            </div>
            <div className="desktop-stand" />
            <div className="desktop-base" />
          </div>
        </div>

        <div className="flexible-flow-lane">
          <div className="flexible-lane-line flexible-lane-upper" />
          <div className="flexible-lane-line flexible-lane-lower" />
          <span className="lane-chip lane-chip-primary">FILE</span>
          <span className="lane-chip lane-chip-secondary">ZIP</span>
          <span className="lane-relay lane-relay-a" />
          <span className="lane-relay lane-relay-b" />
          <span className="lane-relay lane-relay-c" />
          <span className="lane-glow lane-glow-a" />
          <span className="lane-glow lane-glow-b" />
        </div>

        <div className="flexible-device flexible-device-phone">
          <div className="handset-shell flexible-handset">
            <div className="handset-notch" />
            <div className="handset-screen flexible-screen">
              <span className="transfer-device-label">Phone</span>
              <div className="flexible-stack">
                <span className="flexible-stack-item flex-recv-1">IMG_2048</span>
                <span className="flexible-stack-item flex-recv-2">DOC_share</span>
                <span className="flexible-stack-item flex-recv-3">ZIP bundle</span>
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
        <div className="remote-relay">
          <span className="remote-relay-ring remote-relay-ring-a" />
          <span className="remote-relay-ring remote-relay-ring-b" />
          <span className="remote-relay-core" />
        </div>
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
    return <SiGoogleplay className="store-icon" color="#34A853" aria-hidden="true" />;
  }

  if (type === "apple") {
    return <SiAppstore className="store-icon" color="#0D96F6" aria-hidden="true" />;
  }

  if (type === "pc") {
    return (
      <svg className="store-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <rect x="2" y="3" width="20" height="13" rx="2" stroke="currentColor" strokeWidth="1.8" />
        <path d="M8 21h8M12 16v5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      </svg>
    );
  }

  return null;
}

function PlatformIcon({ label }) {
  const palettes = {
    LAN: { bg: "rgba(97, 230, 213, 0.13)", color: "var(--teal)" },
    Tunnel: { bg: "rgba(127, 179, 255, 0.13)", color: "var(--sky)" },
    Control: { bg: "rgba(255, 135, 96, 0.13)", color: "var(--coral)" },
  };
  const { bg, color } = palettes[label] || { bg: "rgba(255,255,255,0.07)", color: "var(--text)" };

  return (
    <span className="platform-icon-wrap" style={{ background: bg, color }}>
      {label === "LAN" && (
        <svg className="platform-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <circle cx="12" cy="17.5" r="1.5" fill="currentColor" />
          <path d="M8.2 13.8a5.5 5.5 0 017.6 0" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
          <path d="M4.5 10.1a10 10 0 0115 0" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
        </svg>
      )}
      {label === "Tunnel" && (
        <svg className="platform-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.7" />
          <ellipse cx="12" cy="12" rx="4.2" ry="9" stroke="currentColor" strokeWidth="1.5" />
          <path d="M3 12h18" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          <path d="M4.5 7.5h15M4.5 16.5h15" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round" opacity="0.5" />
        </svg>
      )}
      {label === "Control" && (
        <svg className="platform-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M12 2L4 6v6c0 4.4 3.4 8.5 8 9.5 4.6-1 8-5.1 8-9.5V6l-8-4z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" />
          <path d="M9 12l2 2 4-4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )}
    </span>
  );
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

function MobileExperienceBand({ band }) {
  return (
    <article className={`mobile-experience-card ${band.tone}`} data-reveal>
      <div className="mobile-experience-copy">
        <p className="band-kicker">{band.kicker}</p>
        <h3>{band.title}</h3>
        <p>{band.body}</p>
      </div>
    </article>
  );
}

function TunnelStatusButton({ locale }) {
  const [open, setOpen] = useState(false);
  const [status, setStatus] = useState("checking");
  const [latency, setLatency] = useState(null);
  const [lastChecked, setLastChecked] = useState(null);
  const [loading, setLoading] = useState(false);

  const check = useCallback(async () => {
    setLoading(true);
    setStatus("checking");
    try {
      const res = await fetch("/api/tunnel-status");
      const json = await res.json();
      setStatus(json.status);
      setLatency(json.latency ?? null);
      setLastChecked(new Date());
    } catch {
      setStatus("offline");
      setLatency(null);
      setLastChecked(new Date());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    check();
  }, [check]);

  const label = locale === "ko" ? "공개 터널 상태" : "Tunnel Status";
  const statusLabel = { checking: locale === "ko" ? "확인 중" : "Checking", online: locale === "ko" ? "온라인" : "Online", offline: locale === "ko" ? "오프라인" : "Offline" }[status];

  const formatTime = (date) => {
    if (!date) return "-";
    return date.toLocaleTimeString(locale === "ko" ? "ko-KR" : "en-US", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  };

  return (
    <div className="tunnel-status-wrap">
      <button type="button" className="tunnel-status-btn" onClick={() => setOpen((prev) => !prev)} aria-expanded={open}>
        <span className={`tunnel-dot dot-${status}`} />
        {label}
      </button>
      {open && (
        <>
          <div className="tunnel-panel-backdrop" onClick={() => setOpen(false)} />
          <div className="tunnel-panel">
            <div className="tunnel-panel-head">
              <strong>{locale === "ko" ? "터널 서버 상태" : "Tunnel Server"}</strong>
              <button type="button" className="tunnel-close-btn" onClick={() => setOpen(false)}>✕</button>
            </div>
            <div className="tunnel-row">
              <span className="tunnel-row-label">{locale === "ko" ? "상태" : "Status"}</span>
              <span className={`tunnel-row-value tunnel-row-status status-${status}`}>
                <span className={`tunnel-dot dot-${status}`} />
                {statusLabel}
              </span>
            </div>
            {latency !== null && (
              <div className="tunnel-row">
                <span className="tunnel-row-label">{locale === "ko" ? "응답 속도" : "Latency"}</span>
                <span className="tunnel-row-value">{latency}ms</span>
              </div>
            )}
            <div className="tunnel-row">
              <span className="tunnel-row-label">{locale === "ko" ? "마지막 확인" : "Last checked"}</span>
              <span className="tunnel-row-value">{formatTime(lastChecked)}</span>
            </div>
            <button type="button" className="tunnel-refresh-btn" onClick={check} disabled={loading}>
              {loading ? (locale === "ko" ? "확인 중..." : "Checking...") : (locale === "ko" ? "새로고침" : "Refresh")}
            </button>
            {status === "offline" && (
              <a className="tunnel-outage-link" href={withLocalePath("/notice", locale)}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.8" />
                  <path d="M12 8v4M12 16h.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                </svg>
                {locale === "ko" ? "장애 공지사항 확인하기" : "View outage notices"}
              </a>
            )}
          </div>
        </>
      )}
    </div>
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
          <a href={withLocalePath("/notice", locale)}>{content.nav.notice}</a>
        </nav>
        <div className="topbar-right">
          <TunnelStatusButton locale={locale} />
          <a className="topbar-action" href={withLocalePath("/tunnel-server", locale)}>
            {content.nav.tunnelServer}
          </a>
        </div>
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
          <a href={withLocalePath("/notice", locale)} onClick={() => setMobileMenuOpen(false)}>
            {content.nav.notice}
          </a>
          <a href={withLocalePath("/tunnel-server", locale)} onClick={() => setMobileMenuOpen(false)}>
            {content.nav.tunnelServer}
          </a>
          <div className="mobile-nav-tunnel-status">
            <TunnelStatusButton locale={locale} />
          </div>
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
            <h1 className="mobile-hero-h1">{content.hero.mobileTitle}</h1>
            <p className="mobile-hero-desc">{content.hero.mobileDescription}</p>
            <div className="mobile-hero-chips">
              <span className="mobile-status-chip chip-lan">
                <span className="status-dot" />
                LAN
              </span>
              <span className="mobile-status-sep" />
              <span className="mobile-status-chip chip-tunnel">
                <span className="status-dot" />
                Tunnel
              </span>
            </div>
            <a className="action-primary mobile-hero-action" href="#download">
              {content.hero.start}
            </a>
          </div>
        </section>
      </section>

      <section className="platform-grid desktop-only">
        {platformCards.map((card) => (
          <article className="platform-card" data-reveal key={card.label}>
            <div className="platform-card-header">
              <PlatformIcon label={card.label} />
            </div>
            <h3>{card.title}</h3>
            <p>{card.body}</p>
            {card.note ? <small className="platform-note">{card.note}</small> : null}
          </article>
        ))}
      </section>

      <section className="mobile-platform-list mobile-only" data-reveal>
        {platformCards.map((card) => (
          <article className="mobile-platform-row" key={card.label}>
            <PlatformIcon label={card.label} />
            <div className="mobile-platform-row-body">
              <strong>{card.title}</strong>
              <p>{card.body}</p>
              {card.note ? <small className="platform-note">{card.note}</small> : null}
            </div>
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
                  {item.store ? <StoreIcon type={item.store} /> : item.platform === "PC" ? <StoreIcon type="pc" /> : null}
                  {item.platform === "PC" ? content.downloads.ctaOpen : content.downloads.ctaDownload}
                </em>
              </a>
            ))}
          </div>
        </section>

        <section className="mobile-download-section mobile-only" data-reveal>
          <div className="mobile-dl-header">
            <p className="section-tag">{content.downloads.tag}</p>
            <h2>{content.downloads.mobileTitle}</h2>
            <p>{content.downloads.mobileBody}</p>
          </div>
          <div className="mobile-download-list">
            {downloads.map((item) => (
              <a className="mobile-dl-tile" href={item.href} key={item.platform}>
                <span className="mobile-dl-icon">
                  <StoreIcon type={item.store || "pc"} />
                </span>
                <span className="mobile-dl-info">
                  <strong>{item.platform}</strong>
                  <span>{item.detail}</span>
                </span>
                <span className="mobile-dl-cta">
                  {item.platform === "PC" ? content.downloads.ctaOpen : content.downloads.ctaDownload}
                  <svg className="mobile-dl-arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M5 12h14M13 6l6 6-6 6" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </span>
              </a>
            ))}
          </div>
        </section>
      </section>

      <SiteFooter footer={content.footer} locale={locale} />
    </main>
  );
}
