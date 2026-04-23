import { headers } from "next/headers";
import { landingContent, tunnelServerContent } from "../content";
import { detectLocale, withLocalePath } from "../locale";
import SiteFooter from "../components/SiteFooter";

const serverSetupCommands = `# GPG public key
curl -fsSL https://rhkr8521.github.io/Tunneler/tunneler-apt-public.key | sudo gpg --dearmor -o /usr/share/keyrings/tunneler-archive-keyring.gpg

# Add APT repository list
echo "deb [signed-by=/usr/share/keyrings/tunneler-archive-keyring.gpg] https://rhkr8521.github.io/Tunneler/repo stable main" | sudo tee /etc/apt/sources.list.d/tunneler.list

# Update APT
sudo apt update`;

const serverSetupCommandsKo = `# GPG 공용키 등록
curl -fsSL https://rhkr8521.github.io/Tunneler/tunneler-apt-public.key | sudo gpg --dearmor -o /usr/share/keyrings/tunneler-archive-keyring.gpg

# APT 저장소 리스트 추가
echo "deb [signed-by=/usr/share/keyrings/tunneler-archive-keyring.gpg] https://rhkr8521.github.io/Tunneler/repo stable main" | sudo tee /etc/apt/sources.list.d/tunneler.list

# APT 업데이트
sudo apt update`;

const installCommand = `sudo apt install tunneler-server`;
const healthCommand = `curl -fsS \\
  -H "Authorization: Bearer <CLIENT_TOKEN>" \\
  "http://<DOMAIN>/_health" | jq .`;
const healthCommandKo = `curl -fsS \\
  -H "Authorization: Bearer <CLIENT_TOKEN>" \\
  "http://<도메인>/_health" | jq .`;

export const metadata = {
  title: "PeerSend Tunnel Server Guide",
  description: "PeerSend Tunnel Server Guide",
};

export default async function TunnelServerPage({ searchParams }) {
  const requestHeaders = await headers();
  const params = await searchParams;
  const locale = detectLocale(params?.lang, requestHeaders.get("accept-language"));
  const content = tunnelServerContent[locale];
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
        <h2>{content.installGuide}</h2>
        <article className="docs-card docs-card-solo">
          <h3>{content.requirements}</h3>
          <h4>{content.server}</h4>
          <ul>
            {content.requirementsItems.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
          <h4>{content.points}</h4>
          <ul>
            {content.pointItems.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </article>
      </section>

      <section className="docs-section">
        <h2>{content.sections.install}</h2>
        <p>{content.sections.installBody}</p>
        <pre className="docs-code">
          <code>{locale === "ko" ? serverSetupCommandsKo : serverSetupCommands}</code>
        </pre>
      </section>

      <section className="docs-section">
        <h2>{content.sections.package}</h2>
        <pre className="docs-code">
          <code>{installCommand}</code>
        </pre>
        <p>{content.sections.packagePrompt}</p>
        <ul>
          {content.sections.packageItems.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </section>

      <section className="docs-section">
        <h2>{content.sections.verify}</h2>
        <div className="docs-card-grid">
          <article className="docs-card">
            <h3>{content.sections.dashboard}</h3>
            <pre className="docs-code compact">
              <code>{locale === "ko" ? "http(s)://<도메인>/dashboard" : "http(s)://<domain>/dashboard"}</code>
            </pre>
          </article>
          <article className="docs-card">
            <h3>{content.sections.status}</h3>
            <pre className="docs-code compact">
              <code>sudo systemctl status tunneler-server -l</code>
            </pre>
          </article>
          <article className="docs-card">
            <h3>{content.sections.logs}</h3>
            <pre className="docs-code compact">
              <code>sudo journalctl -u tunneler-server -f</code>
            </pre>
          </article>
          <article className="docs-card">
            <h3>{content.sections.health}</h3>
            <pre className="docs-code compact">
              <code>{locale === "ko" ? healthCommandKo : healthCommand}</code>
            </pre>
          </article>
        </div>
      </section>

      <SiteFooter footer={footer} locale={locale} />
    </main>
  );
}
