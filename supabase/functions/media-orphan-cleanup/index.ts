import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import { verifySchedulerCaller } from "../_shared/auth.ts";

const headers = {
  "Content-Type": "application/json",
  "Connection": "keep-alive",
};

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers });
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers });
  if (request.method !== "POST") return json(405, { error: "POST is required." });

  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
  if (!url || !serviceKey) return json(503, { error: "Media cleanup is not configured." });

  const service = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  try {
    await verifySchedulerCaller(request, service);
  } catch {
    return json(401, { error: "Unauthorized media-cleanup request." });
  }

  try {
    const body = await request.json().catch(() => ({}));
    const requestedLimit = typeof body?.limit === "number" ? body.limit : 100;
    const limit = Math.max(1, Math.min(Math.trunc(requestedLimit), 500));
    const { data: candidates, error: candidateError } = await service.rpc("media_orphan_candidates", {
      p_cutoff: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
      p_limit: limit,
    });
    if (candidateError) throw candidateError;

    const removed: string[] = [];
    const failures: string[] = [];
    for (const bucket of ["rtc-community-media", "civic-report-evidence"]) {
      const paths = (candidates ?? [])
        .filter((candidate: { bucket_id: string }) => candidate.bucket_id === bucket)
        .map((candidate: { name: string }) => candidate.name)
        .filter((path: string) => path.length > 0);
      if (paths.length === 0) continue;
      const { data, error } = await service.storage.from(bucket).remove(paths);
      if (error) {
        failures.push(`${bucket}:${error.message}`);
      } else {
        removed.push(...(data ?? []).map((item: { name: string }) => `${bucket}/${item.name}`));
      }
    }

    return json(failures.length > 0 ? 207 : 200, {
      scanned: (candidates ?? []).length,
      removed: removed.length,
      failures,
    });
  } catch {
    return json(500, { error: "Media cleanup could not complete." });
  }
});
