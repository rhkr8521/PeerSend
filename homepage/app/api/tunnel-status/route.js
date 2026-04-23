export async function GET() {
  const t0 = Date.now();
  try {
    const res = await fetch("https://tunnel-peersend.kro.kr/health", {
      signal: AbortSignal.timeout(6000),
      cache: "no-store",
    });
    const latency = Date.now() - t0;
    let data = null;
    try {
      data = await res.json();
    } catch {}
    return Response.json({
      status: res.ok ? "online" : "offline",
      httpStatus: res.status,
      latency,
      data,
    });
  } catch (e) {
    return Response.json({
      status: "offline",
      latency: null,
      error: e.message,
    });
  }
}
