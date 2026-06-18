function unauthorized(message) {
  return new Response(message, { status: 401 });
}

function tooManyRequests(message, headers = {}) {
  return new Response(message, {
    status: 429,
    headers
  });
}

function badRequest(message) {
  return new Response(message, { status: 400 });
}

function unsupportedMediaType(message) {
  return new Response(message, { status: 415 });
}

function serverError(message) {
  return new Response(message, { status: 502 });
}

function jsonResponse(value, init = {}) {
  return new Response(JSON.stringify(value), {
    ...init,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...(init.headers || {})
    }
  });
}

const FIREBASE_CERTS_URL =
  "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";

let firebaseCertCache = {
  certsByKid: null,
  expiresAtMs: 0
};

const DEFAULT_FREE_TIER_DAILY_TRANSCRIPT_LIMIT = 5;

function arrayBufferToBase64(arrayBuffer) {
  let binary = "";
  const bytes = new Uint8Array(arrayBuffer);
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    const chunk = bytes.subarray(i, i + chunkSize);
    binary += String.fromCharCode(...chunk);
  }
  return btoa(binary);
}

function extractTranscriptText(result) {
  if (typeof result === "string") {
    return result.trim();
  }

  if (result && typeof result === "object") {
    if (typeof result.text === "string") {
      return result.text.trim();
    }
    if (typeof result.transcript === "string") {
      return result.transcript.trim();
    }
    if (typeof result.result?.text === "string") {
      return result.result.text.trim();
    }
    if (typeof result.transcription_info?.text === "string") {
      return result.transcription_info.text.trim();
    }
  }

  return "";
}

function base64UrlToBase64(input) {
  const base64 = input.replace(/-/g, "+").replace(/_/g, "/");
  const remainder = base64.length % 4;
  if (remainder === 0) {
    return base64;
  }
  return base64 + "=".repeat(4 - remainder);
}

function base64UrlDecodeToString(input) {
  const normalized = base64UrlToBase64(input);
  return atob(normalized);
}

function parseJwtJsonSegment(segment, label) {
  try {
    return JSON.parse(base64UrlDecodeToString(segment));
  } catch {
    throw new Error(`Firebase ID token has an invalid ${label}.`);
  }
}

function base64UrlDecodeToBytes(input) {
  const normalized = base64UrlToBase64(input);
  const binary = atob(normalized);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function pemToDerBytes(pem) {
  const base64 = pem
    .replace("-----BEGIN CERTIFICATE-----", "")
    .replace("-----END CERTIFICATE-----", "")
    .replace(/\s+/g, "");

  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function readDerElement(bytes, offset) {
  const tag = bytes[offset];
  const lengthByte = bytes[offset + 1];
  let length = 0;
  let lengthBytes = 1;

  if ((lengthByte & 0x80) === 0) {
    length = lengthByte;
  } else {
    const longFormBytes = lengthByte & 0x7f;
    lengthBytes += longFormBytes;
    for (let i = 0; i < longFormBytes; i += 1) {
      length = (length << 8) | bytes[offset + 2 + i];
    }
  }

  const headerLength = 1 + lengthBytes;
  return {
    tag,
    offset,
    headerLength,
    valueOffset: offset + headerLength,
    length,
    end: offset + headerLength + length
  };
}

function extractSpkiFromCertificate(certPem) {
  const certBytes = pemToDerBytes(certPem);
  const certSequence = readDerElement(certBytes, 0);
  const tbsCertificate = readDerElement(certBytes, certSequence.valueOffset);

  let cursor = tbsCertificate.valueOffset;
  if (certBytes[cursor] === 0xa0) {
    cursor = readDerElement(certBytes, cursor).end;
  }

  cursor = readDerElement(certBytes, cursor).end; // serialNumber
  cursor = readDerElement(certBytes, cursor).end; // signature
  cursor = readDerElement(certBytes, cursor).end; // issuer
  cursor = readDerElement(certBytes, cursor).end; // validity
  cursor = readDerElement(certBytes, cursor).end; // subject

  const subjectPublicKeyInfo = readDerElement(certBytes, cursor);
  return certBytes.slice(subjectPublicKeyInfo.offset, subjectPublicKeyInfo.end);
}

async function importFirebasePublicKey(certPem) {
  const spkiBytes = extractSpkiFromCertificate(certPem);
  return crypto.subtle.importKey(
    "spki",
    spkiBytes,
    {
      name: "RSASSA-PKCS1-v1_5",
      hash: "SHA-256"
    },
    false,
    ["verify"]
  );
}

function parseMaxAgeSeconds(cacheControl) {
  if (!cacheControl) {
    return 3600;
  }
  const match = cacheControl.match(/max-age=(\d+)/i);
  return match ? Number.parseInt(match[1], 10) : 3600;
}

async function fetchFirebaseCerts() {
  if (firebaseCertCache.certsByKid && Date.now() < firebaseCertCache.expiresAtMs) {
    return firebaseCertCache.certsByKid;
  }

  const response = await fetch(FIREBASE_CERTS_URL);
  if (!response.ok) {
    throw new Error(`Could not fetch Firebase signing certs (${response.status}).`);
  }

  const certsByKid = await response.json();
  const maxAgeSeconds = parseMaxAgeSeconds(response.headers.get("Cache-Control"));
  firebaseCertCache = {
    certsByKid,
    expiresAtMs: Date.now() + maxAgeSeconds * 1000
  };

  return certsByKid;
}

function validateFirebaseTokenClaims(header, payload, projectId) {
  const nowSeconds = Math.floor(Date.now() / 1000);

  if (header.alg !== "RS256") {
    throw new Error("Firebase ID token must use RS256.");
  }

  if (typeof header.kid !== "string" || header.kid.length === 0) {
    throw new Error("Firebase ID token is missing a key ID.");
  }

  if (payload.aud !== projectId) {
    throw new Error("Firebase ID token audience does not match this project.");
  }

  if (payload.iss !== `https://securetoken.google.com/${projectId}`) {
    throw new Error("Firebase ID token issuer does not match this project.");
  }

  if (typeof payload.sub !== "string" || payload.sub.length === 0) {
    throw new Error("Firebase ID token subject is missing.");
  }

  if (typeof payload.exp !== "number" || payload.exp <= nowSeconds) {
    throw new Error("Firebase ID token has expired.");
  }

  if (typeof payload.iat !== "number" || payload.iat > nowSeconds) {
    throw new Error("Firebase ID token issued-at time is invalid.");
  }

  if (typeof payload.auth_time !== "number" || payload.auth_time > nowSeconds) {
    throw new Error("Firebase ID token auth_time is invalid.");
  }
}

async function verifyFirebaseIdToken(idToken, env) {
  const projectId = env.FIREBASE_PROJECT_ID;
  if (!projectId) {
    throw new Error("FIREBASE_PROJECT_ID is not configured.");
  }

  const segments = idToken.split(".");
  if (segments.length !== 3) {
    throw new Error("Firebase ID token must have exactly 3 segments.");
  }

  const [headerSegment, payloadSegment, signatureSegment] = segments;
  const header = parseJwtJsonSegment(headerSegment, "header");
  const payload = parseJwtJsonSegment(payloadSegment, "payload");
  validateFirebaseTokenClaims(header, payload, projectId);

  const certsByKid = await fetchFirebaseCerts();
  const certPem = certsByKid[header.kid];
  if (!certPem) {
    throw new Error("Firebase ID token key ID is not recognized.");
  }

  const publicKey = await importFirebasePublicKey(certPem);
  const signatureBytes = base64UrlDecodeToBytes(signatureSegment);
  const signedBytes = new TextEncoder().encode(`${headerSegment}.${payloadSegment}`);

  const verified = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    publicKey,
    signatureBytes,
    signedBytes
  );

  if (!verified) {
    throw new Error("Firebase ID token signature verification failed.");
  }

  return payload;
}

async function verifyBearerTokenRequest(request, env) {
  const authorization = request.headers.get("Authorization");
  if (!authorization || !authorization.startsWith("Bearer ")) {
    return {
      ok: false,
      response: unauthorized("Missing bearer token.")
    };
  }
  const idToken = authorization.slice("Bearer ".length).trim();
  if (!idToken) {
    return {
      ok: false,
      response: unauthorized("Missing bearer token.")
    };
  }

  try {
    const verifier = env.__verifyFirebaseIdToken || verifyFirebaseIdToken;
    const payload = await verifier(idToken, env);
    const uid = payload?.sub || payload?.uid;
    if (typeof uid !== "string" || uid.length === 0) {
      return {
        ok: false,
        response: unauthorized("Firebase ID token subject is missing.")
      };
    }
    return {
      ok: true,
      payload,
      uid
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "Firebase ID token verification failed.";
    return {
      ok: false,
      response: unauthorized(message)
    };
  }
}

function getFreeTierDailyTranscriptLimit(env) {
  const rawLimit = env.FREE_TIER_DAILY_TRANSCRIPT_LIMIT;
  const parsedLimit = Number.parseInt(rawLimit ?? "", 10);
  if (Number.isFinite(parsedLimit) && parsedLimit > 0) {
    return parsedLimit;
  }
  return DEFAULT_FREE_TIER_DAILY_TRANSCRIPT_LIMIT;
}

function getUtcDayKey(now = new Date()) {
  const year = now.getUTCFullYear();
  const month = String(now.getUTCMonth() + 1).padStart(2, "0");
  const day = String(now.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function getSecondsUntilNextUtcMidnight(now = new Date()) {
  const nextMidnightUtcMs = Date.UTC(
    now.getUTCFullYear(),
    now.getUTCMonth(),
    now.getUTCDate() + 1,
    0,
    0,
    0,
    0
  );
  const diffSeconds = Math.ceil((nextMidnightUtcMs - now.getTime()) / 1000);
  return Math.max(60, diffSeconds);
}

function getUsageNow(env) {
  if (env.__now instanceof Date) {
    return env.__now;
  }
  return new Date();
}

function createUsageKey(uid, now = new Date()) {
  return `usage:${uid}:${getUtcDayKey(now)}`;
}

async function readUsageCount(env, uid, now = getUsageNow(env)) {
  if (!env.USAGE_KV || typeof env.USAGE_KV.get !== "function") {
    return {
      count: 0,
      key: null,
      ttlSeconds: null
    };
  }

  const key = createUsageKey(uid, now);
  const currentValue = await env.USAGE_KV.get(key);
  const parsedCount = Number.parseInt(currentValue ?? "0", 10);
  return {
    count: Number.isFinite(parsedCount) && parsedCount >= 0 ? parsedCount : 0,
    key,
    ttlSeconds: getSecondsUntilNextUtcMidnight(now)
  };
}

async function enforceDailyTranscriptLimit(env, uid, now = getUsageNow(env)) {
  const limit = getFreeTierDailyTranscriptLimit(env);
  const usage = await readUsageCount(env, uid, now);
  const remaining = Math.max(0, limit - usage.count);

  if (usage.count >= limit) {
    return {
      allowed: false,
      usage,
      limit,
      remaining
    };
  }

  return {
    allowed: true,
    usage,
    limit,
    remaining
  };
}

async function recordSuccessfulTranscript(env, uid, usageState) {
  const nextCount = usageState.usage.count + 1;
  if (!env.USAGE_KV || typeof env.USAGE_KV.put !== "function" || !usageState.usage.key) {
    return nextCount;
  }

  await env.USAGE_KV.put(usageState.usage.key, String(nextCount), {
    expirationTtl: usageState.usage.ttlSeconds
  });
  return nextCount;
}

function buildQuotaHeaders(limit, usedCount) {
  return {
    "X-ClipScribe-Free-Limit": String(limit),
    "X-ClipScribe-Free-Used": String(usedCount),
    "X-ClipScribe-Free-Remaining": String(Math.max(0, limit - usedCount))
  };
}

async function transcribeWithMock(env, audioBytes) {
  const secondsEstimate = Math.max(1, Math.round(audioBytes.byteLength / 32000));
  return {
    text: env.MOCK_TRANSCRIPTION_TEXT || `[MOCK] Received about ${secondsEstimate}s of audio.`,
    durationMs: 25
  };
}

async function transcribeWithCloudflareBinding(env, audioBytes) {
  if (!env.AI || typeof env.AI.run !== "function") {
    throw new Error("Cloudflare AI binding is not configured.");
  }

  const model = env.TRANSCRIPTION_MODEL || "@cf/openai/whisper-large-v3-turbo";
  const language = env.TRANSCRIPTION_LANGUAGE;

  let input;
  if (model === "@cf/openai/whisper-tiny-en") {
    input = {
      audio: Array.from(new Uint8Array(audioBytes))
    };
  } else {
    input = {
      audio: arrayBufferToBase64(audioBytes)
    };
  }

  if (language) {
    input.language = language;
  }

  const aiResult = await env.AI.run(model, input);

  const text = extractTranscriptText(aiResult);
  if (!text) {
    throw new Error("Cloudflare AI returned an empty transcript.");
  }

  return {
    text,
    durationMs: null
  };
}

async function transcribeAudio(env, audioBytes) {
  const provider = env.TRANSCRIPTION_PROVIDER || "mock";
  if (provider === "mock") {
    return transcribeWithMock(env, audioBytes);
  }
  if (provider === "cloudflare-binding") {
    return transcribeWithCloudflareBinding(env, audioBytes);
  }
  throw new Error(`Unsupported transcription provider: ${provider}`);
}

export async function handleTranscribeRequest(request, env) {
  if (request.method !== "POST") {
    return new Response("Method not allowed.", { status: 405 });
  }

  const authResult = await verifyBearerTokenRequest(request, env);
  if (!authResult.ok) {
    return authResult.response;
  }

  const usageUid = authResult.uid;
  const usageState = await enforceDailyTranscriptLimit(env, usageUid);
  if (!usageState.allowed) {
    return tooManyRequests(
      "Free transcript limit reached for today. Try again tomorrow.",
      buildQuotaHeaders(usageState.limit, usageState.usage.count)
    );
  }

  const contentType = request.headers.get("Content-Type") || "";
  if (!contentType.toLowerCase().startsWith("audio/wav")) {
    return unsupportedMediaType("Expected Content-Type audio/wav.");
  }

  const audioBytes = await request.arrayBuffer();
  if (audioBytes.byteLength === 0) {
    return badRequest("Audio body is empty.");
  }

  try {
    const transcript = await transcribeAudio(env, audioBytes);
    const usedCount = await recordSuccessfulTranscript(env, usageUid, usageState);
    return new Response(transcript.text, {
      status: 200,
      headers: {
        "Content-Type": "text/plain; charset=utf-8",
        ...buildQuotaHeaders(usageState.limit, usedCount),
        ...(transcript.durationMs == null
          ? {}
          : { "X-ClipScribe-Transcription-Duration-Ms": String(transcript.durationMs) })
      }
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Transcription provider failed.";
    return serverError(message);
  }
}

function getTranscriptDb(env) {
  return env.TRANSCRIPTS_DB || env.__transcriptsDb || null;
}

function normalizeTranscriptRow(row) {
  return {
    id: String(row.id),
    text: String(row.text || ""),
    sourceDurationSeconds: Number(row.source_duration_seconds || 0),
    createdAt: String(row.created_at || "")
  };
}

async function parseTranscriptBody(request) {
  try {
    return await request.json();
  } catch {
    throw new Error("Expected JSON body.");
  }
}

async function handleCreateTranscriptRequest(request, env, uid) {
  const db = getTranscriptDb(env);
  if (!db || typeof db.prepare !== "function") {
    return serverError("Transcript database is not configured.");
  }

  let body;
  try {
    body = await parseTranscriptBody(request);
  } catch (error) {
    return badRequest(error.message);
  }

  const text = typeof body.text === "string" ? body.text.trim() : "";
  if (!text) {
    return badRequest("Transcript text is required.");
  }

  const sourceDurationSeconds = Number.isFinite(body.sourceDurationSeconds)
    ? Math.max(1, Math.round(body.sourceDurationSeconds))
    : 1;
  const id = crypto.randomUUID();
  const createdAt = new Date().toISOString();

  await db
    .prepare(
      `INSERT INTO transcripts (id, uid, text, source_duration_seconds, created_at)
       VALUES (?, ?, ?, ?, ?)`
    )
    .bind(id, uid, text, sourceDurationSeconds, createdAt)
    .run();

  return jsonResponse(
    {
      transcript: {
        id,
        text,
        sourceDurationSeconds,
        createdAt
      }
    },
    { status: 201 }
  );
}

async function handleListTranscriptsRequest(env, uid) {
  const db = getTranscriptDb(env);
  if (!db || typeof db.prepare !== "function") {
    return serverError("Transcript database is not configured.");
  }

  const result = await db
    .prepare(
      `SELECT id, text, source_duration_seconds, created_at
       FROM transcripts
       WHERE uid = ?
       ORDER BY created_at DESC
       LIMIT 50`
    )
    .bind(uid)
    .all();

  return jsonResponse({
    transcripts: (result.results || []).map(normalizeTranscriptRow)
  });
}

async function handleDeleteTranscriptRequest(env, uid, transcriptId) {
  const db = getTranscriptDb(env);
  if (!db || typeof db.prepare !== "function") {
    return serverError("Transcript database is not configured.");
  }

  if (!transcriptId) {
    return badRequest("Transcript ID is required.");
  }

  await db
    .prepare("DELETE FROM transcripts WHERE uid = ? AND id = ?")
    .bind(uid, transcriptId)
    .run();

  return new Response(null, { status: 204 });
}

export async function handleTranscriptPersistenceRequest(request, env, transcriptId = null) {
  const authResult = await verifyBearerTokenRequest(request, env);
  if (!authResult.ok) {
    return authResult.response;
  }

  if (request.method === "POST" && transcriptId == null) {
    return handleCreateTranscriptRequest(request, env, authResult.uid);
  }

  if (request.method === "GET" && transcriptId == null) {
    return handleListTranscriptsRequest(env, authResult.uid);
  }

  if (request.method === "DELETE" && transcriptId != null) {
    return handleDeleteTranscriptRequest(env, authResult.uid, transcriptId);
  }

  return new Response("Method not allowed.", { status: 405 });
}

export async function handleWaitlistRequest(request, env) {
  const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
  };

  if (request.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: corsHeaders,
    });
  }

  if (request.method !== "POST") {
    return new Response("Method not allowed.", {
      status: 405,
      headers: corsHeaders,
    });
  }

  let body;
  try {
    body = await request.json();
  } catch (error) {
    return new Response(JSON.stringify({ error: "Expected JSON body." }), {
      status: 400,
      headers: {
        ...corsHeaders,
        "Content-Type": "application/json",
      },
    });
  }

  const email = typeof body.email === "string" ? body.email.trim() : "";
  const platform = typeof body.platform === "string" ? body.platform.trim().toLowerCase() : "";

  if (!email || !email.includes("@")) {
    return new Response(JSON.stringify({ error: "A valid email is required." }), {
      status: 400,
      headers: {
        ...corsHeaders,
        "Content-Type": "application/json",
      },
    });
  }

  if (platform !== "android" && platform !== "ios" && platform !== "other") {
    return new Response(JSON.stringify({ error: "Platform must be 'android', 'ios', or 'other'." }), {
      status: 400,
      headers: {
        ...corsHeaders,
        "Content-Type": "application/json",
      },
    });
  }

  const db = getTranscriptDb(env);
  if (!db || typeof db.prepare !== "function") {
    return new Response(JSON.stringify({ error: "Database not configured." }), {
      status: 500,
      headers: {
        ...corsHeaders,
        "Content-Type": "application/json",
      },
    });
  }

  try {
    const createdAt = new Date().toISOString();
    await db
      .prepare(
        `INSERT INTO waitlist (email, platform, created_at)
         VALUES (?, ?, ?)`
      )
      .bind(email, platform, createdAt)
      .run();

    return new Response(JSON.stringify({ success: true }), {
      status: 201,
      headers: {
        ...corsHeaders,
        "Content-Type": "application/json",
      },
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "";
    if (message.includes("UNIQUE constraint failed")) {
      return new Response(JSON.stringify({ success: true, message: "You are already on the waitlist!" }), {
        status: 200,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json",
        },
      });
    }
    return new Response(JSON.stringify({ error: "Failed to join waitlist. Please try again later." }), {
      status: 500,
      headers: {
        ...corsHeaders,
        "Content-Type": "application/json",
      },
    });
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/waitlist") {
      return handleWaitlistRequest(request, env);
    }
    if (url.pathname === "/transcribe") {
      return handleTranscribeRequest(request, env);
    }
    if (url.pathname === "/transcripts") {
      return handleTranscriptPersistenceRequest(request, env);
    }
    const transcriptMatch = url.pathname.match(/^\/transcripts\/([^/]+)$/);
    if (transcriptMatch) {
      return handleTranscriptPersistenceRequest(
        request,
        env,
        decodeURIComponent(transcriptMatch[1])
      );
    }
    return new Response("Not found.", { status: 404 });
  }
};

