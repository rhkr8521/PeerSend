import { readFileSync, writeFileSync, mkdirSync } from "fs";
import { join } from "path";

const DATA_DIR = join(process.cwd(), "data");
const DATA_FILE = join(DATA_DIR, "tunnel-health.json");
const TUNNEL_URL = "https://tunneler-peersend.kro.kr/_health";
const TUNNEL_TOKEN = "public-p2p-token-8521";
const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;
const STALE_MS = 5 * 60 * 1000;

function readData() {
  try {
    return JSON.parse(readFileSync(DATA_FILE, "utf-8"));
  } catch {
    return { checks: [] };
  }
}

function writeData(data) {
  try {
    mkdirSync(DATA_DIR, { recursive: true });
    writeFileSync(DATA_FILE, JSON.stringify(data));
  } catch {}
}

async function runCheck(data) {
  const t0 = Date.now();
  let ok = false;
  let latency = null;
  try {
    const res = await fetch(TUNNEL_URL, {
      headers: { Authorization: `Bearer ${TUNNEL_TOKEN}` },
      signal: AbortSignal.timeout(6000),
      cache: "no-store",
    });
    latency = Date.now() - t0;
    ok = res.ok;
  } catch {}

  const now = Date.now();
  data.checks.push({ t: now, ok, ...(latency !== null ? { latency } : {}) });
  data.checks = data.checks.filter((c) => c.t >= now - SEVEN_DAYS_MS);
  return { ok, latency };
}

function calculateDowntime(sortedChecks) {
  let downtimeMs = 0;
  let failStart = null;

  for (let i = 0; i < sortedChecks.length; i++) {
    if (!sortedChecks[i].ok && failStart === null) {
      failStart = i > 0 ? sortedChecks[i - 1].t : sortedChecks[i].t;
    } else if (sortedChecks[i].ok && failStart !== null) {
      downtimeMs += sortedChecks[i].t - failStart;
      failStart = null;
    }
  }
  if (failStart !== null && sortedChecks.length > 0) {
    downtimeMs += sortedChecks[sortedChecks.length - 1].t - failStart;
  }
  return downtimeMs;
}

function getDayStatus(dayChecks, isToday, currentOk) {
  if (dayChecks.length === 0) return "unknown";

  const sorted = [...dayChecks].sort((a, b) => a.t - b.t);

  if (isToday) {
    if (!currentOk) return "red";
    const hasAnyDown = sorted.some((c) => !c.ok);
    return hasAnyDown ? "yellow" : "green";
  }

  const downtimeMs = calculateDowntime(sorted);
  if (downtimeMs === 0) return "green";
  if (downtimeMs >= 3 * 60 * 60 * 1000) return "red";
  return "yellow";
}

function summarize(data) {
  const now = Date.now();
  const latest = data.checks.length > 0 ? data.checks[data.checks.length - 1] : null;
  const currentOk = latest ? latest.ok : null;

  // find last !ok → ok transition timestamp
  let recoveredAt = null;
  const sorted = [...data.checks].sort((a, b) => a.t - b.t);
  for (let i = 1; i < sorted.length; i++) {
    if (!sorted[i - 1].ok && sorted[i].ok) {
      recoveredAt = sorted[i].t;
    }
  }

  const weekly = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now);
    d.setDate(d.getDate() - i);
    d.setHours(0, 0, 0, 0);
    const nextD = new Date(d);
    nextD.setDate(nextD.getDate() + 1);

    const dayChecks = data.checks.filter((c) => c.t >= d.getTime() && c.t < nextD.getTime());
    weekly.push({
      date: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`,
      status: getDayStatus(dayChecks, i === 0, currentOk),
    });
  }

  return {
    weekly,
    currentStatus: latest === null ? "unknown" : latest.ok ? "online" : "offline",
    lastChecked: latest?.t ?? null,
    latency: latest?.latency ?? null,
    recoveredAt,
  };
}

export async function GET() {
  const data = readData();
  const latest = data.checks[data.checks.length - 1];

  if (!latest || Date.now() - latest.t > STALE_MS) {
    await runCheck(data);
    writeData(data);
  }

  return Response.json(summarize(data));
}

export async function POST() {
  const data = readData();
  const result = await runCheck(data);
  writeData(data);
  return Response.json({ ...result, t: Date.now() });
}
