function unauthorized(message) {
  return new Response(message, { status: 401 });
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
  }

  return "";
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

  const aiResult = await env.AI.run("@cf/openai/whisper", {
    audio: Array.from(new Uint8Array(audioBytes))
  });

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

  const authorization = request.headers.get("Authorization");
  if (!authorization || !authorization.startsWith("Bearer ")) {
    return unauthorized("Missing bearer token.");
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
    return new Response(transcript.text, {
      status: 200,
      headers: {
        "Content-Type": "text/plain; charset=utf-8",
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

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/transcribe") {
      return handleTranscribeRequest(request, env);
    }
    return new Response("Not found.", { status: 404 });
  }
};
