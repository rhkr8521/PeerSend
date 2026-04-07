import { headers } from "next/headers";
import LandingClient from "./LandingClient";
import { detectLocale } from "./locale";

export const dynamic = "force-dynamic";

export default async function HomePage({ searchParams }) {
  const requestHeaders = await headers();
  const params = await searchParams;
  const locale = detectLocale(params?.lang, requestHeaders.get("accept-language"));

  return <LandingClient initialLocale={locale} />;
}
