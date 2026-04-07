import { headers } from "next/headers";
import LandingClient from "./LandingClient";
import { detectLocaleFromLanguage } from "./locale";

export const dynamic = "force-dynamic";

export default async function HomePage() {
  const requestHeaders = await headers();
  const locale = detectLocaleFromLanguage(requestHeaders.get("accept-language"));

  return <LandingClient initialLocale={locale} />;
}
