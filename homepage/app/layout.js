import "./globals.css";
import { headers } from "next/headers";
import { detectLocaleFromLanguage } from "./locale";

export const metadata = {
  title: "PeerSend",
  description: "PC와 휴대폰, 휴대폰과 휴대폰 사이를 빠르게 잇는 PeerSend 공식 홈페이지",
  icons: {
    icon: "/icon.png",
    shortcut: "/icon.png",
    apple: "/icon.png",
  },
};

export default async function RootLayout({ children }) {
  const requestHeaders = await headers();
  const locale = detectLocaleFromLanguage(requestHeaders.get("accept-language"));

  return (
    <html lang={locale}>
      <body>{children}</body>
    </html>
  );
}
