import test from "node:test";
import assert from "node:assert/strict";
import worker, { handleTranscribeRequest } from "../src/index.mjs";

function makeRequest(overrides = {}) {
  return new Request("https://example.com/transcribe", {
    method: "POST",
    headers: {
      Authorization: "Bearer firebase-token",
      "Content-Type": "audio/wav",
      ...overrides.headers
    },
    body: overrides.body ?? new Uint8Array([1, 2, 3, 4])
  });
}

function makeMemoryKv(initialValues = {}) {
  const values = new Map(Object.entries(initialValues));
  return {
    async get(key) {
      return values.has(key) ? values.get(key) : null;
    },
    async put(key, value) {
      values.set(key, value);
    },
    snapshot() {
      return new Map(values);
    }
  };
}

test("returns 401 when bearer token is missing", async () => {
  const response = await handleTranscribeRequest(
    makeRequest({
      headers: {
        Authorization: ""
      }
    }),
    {
      FIREBASE_PROJECT_ID: "clipscribe-e3668",
      TRANSCRIPTION_PROVIDER: "mock",
      MOCK_TRANSCRIPTION_TEXT: "ignored"
    }
  );

  assert.equal(response.status, 401);
  assert.equal(await response.text(), "Missing bearer token.");
});

test("returns 200 and transcript text for mock provider", async () => {
  const response = await handleTranscribeRequest(
    makeRequest(),
    {
      FIREBASE_PROJECT_ID: "clipscribe-e3668",
      TRANSCRIPTION_PROVIDER: "mock",
      MOCK_TRANSCRIPTION_TEXT: "Transcript from worker.",
      async __verifyFirebaseIdToken() {
        return { uid: "user-1" };
      }
    }
  );

  assert.equal(response.status, 200);
  assert.equal(await response.text(), "Transcript from worker.");
  assert.equal(response.headers.get("X-ClipScribe-Transcription-Duration-Ms"), "25");
});

test("returns 415 for non wav payloads", async () => {
  const response = await handleTranscribeRequest(
    makeRequest({
      headers: {
        "Content-Type": "application/json"
      }
    }),
    {
      FIREBASE_PROJECT_ID: "clipscribe-e3668",
      TRANSCRIPTION_PROVIDER: "mock",
      MOCK_TRANSCRIPTION_TEXT: "ignored",
      async __verifyFirebaseIdToken() {
        return { uid: "user-1" };
      }
    }
  );

  assert.equal(response.status, 415);
  assert.equal(await response.text(), "Expected Content-Type audio/wav.");
});

test("worker routes transcribe path and rejects unknown paths", async () => {
  const okResponse = await worker.fetch(makeRequest(), {
    FIREBASE_PROJECT_ID: "clipscribe-e3668",
    TRANSCRIPTION_PROVIDER: "mock",
    MOCK_TRANSCRIPTION_TEXT: "OK",
    async __verifyFirebaseIdToken() {
      return { uid: "user-1" };
    }
  });
  assert.equal(okResponse.status, 200);

  const notFoundResponse = await worker.fetch(
    new Request("https://example.com/other"),
    { FIREBASE_PROJECT_ID: "clipscribe-e3668", TRANSCRIPTION_PROVIDER: "mock" }
  );
  assert.equal(notFoundResponse.status, 404);
});

test("returns 401 when Firebase token verification fails", async () => {
  const response = await handleTranscribeRequest(
    makeRequest(),
    {
      FIREBASE_PROJECT_ID: "clipscribe-e3668",
      TRANSCRIPTION_PROVIDER: "mock",
      async __verifyFirebaseIdToken() {
        throw new Error("Firebase ID token signature verification failed.");
      }
    }
  );

  assert.equal(response.status, 401);
  assert.equal(await response.text(), "Firebase ID token signature verification failed.");
});

test("uses Cloudflare binding with base64 audio for whisper-large-v3-turbo", async () => {
  let receivedModel = null;
  let receivedInput = null;
  const response = await handleTranscribeRequest(
    makeRequest(),
    {
      FIREBASE_PROJECT_ID: "clipscribe-e3668",
      TRANSCRIPTION_PROVIDER: "cloudflare-binding",
      TRANSCRIPTION_MODEL: "@cf/openai/whisper-large-v3-turbo",
      TRANSCRIPTION_LANGUAGE: "en",
      async __verifyFirebaseIdToken() {
        return { uid: "user-1" };
      },
      AI: {
        async run(model, input) {
          receivedModel = model;
          receivedInput = input;
          return { text: "real transcript" };
        }
      }
    }
  );

  assert.equal(response.status, 200);
  assert.equal(await response.text(), "real transcript");
  assert.equal(receivedModel, "@cf/openai/whisper-large-v3-turbo");
  assert.equal(typeof receivedInput.audio, "string");
  assert.equal(receivedInput.language, "en");
  assert.equal(response.headers.get("X-ClipScribe-Free-Limit"), "5");
  assert.equal(response.headers.get("X-ClipScribe-Free-Used"), "1");
  assert.equal(response.headers.get("X-ClipScribe-Free-Remaining"), "4");
});

test("uses Cloudflare binding with byte array audio for whisper-tiny-en", async () => {
  let receivedModel = null;
  let receivedInput = null;
  const response = await handleTranscribeRequest(
    makeRequest({
      body: new Uint8Array([10, 20, 30, 40])
    }),
    {
      FIREBASE_PROJECT_ID: "clipscribe-e3668",
      TRANSCRIPTION_PROVIDER: "cloudflare-binding",
      TRANSCRIPTION_MODEL: "@cf/openai/whisper-tiny-en",
      async __verifyFirebaseIdToken() {
        return { uid: "user-1" };
      },
      AI: {
        async run(model, input) {
          receivedModel = model;
          receivedInput = input;
          return { transcription_info: { text: "tiny transcript" } };
        }
      }
    }
  );

  assert.equal(response.status, 200);
  assert.equal(await response.text(), "tiny transcript");
  assert.equal(receivedModel, "@cf/openai/whisper-tiny-en");
  assert.deepEqual(receivedInput.audio, [10, 20, 30, 40]);
});

test("returns 429 when verified user has reached the daily free transcript limit", async () => {
  const usageKv = makeMemoryKv({
    "usage:user-1:2026-06-16": "2"
  });

  const response = await handleTranscribeRequest(
    makeRequest(),
    {
      FIREBASE_PROJECT_ID: "clipscribe-e3668",
      FREE_TIER_DAILY_TRANSCRIPT_LIMIT: "2",
      __now: new Date("2026-06-16T12:00:00Z"),
      TRANSCRIPTION_PROVIDER: "mock",
      MOCK_TRANSCRIPTION_TEXT: "ignored",
      USAGE_KV: usageKv,
      async __verifyFirebaseIdToken() {
        return { sub: "user-1" };
      }
    }
  );

  assert.equal(response.status, 429);
  assert.equal(
    await response.text(),
    "Free transcript limit reached for today. Sign in later or upgrade when saved transcripts are available."
  );
  assert.equal(response.headers.get("X-ClipScribe-Free-Limit"), "2");
  assert.equal(response.headers.get("X-ClipScribe-Free-Used"), "2");
  assert.equal(response.headers.get("X-ClipScribe-Free-Remaining"), "0");
});

test("increments daily usage count after a successful transcript", async () => {
  const usageKv = makeMemoryKv({
    "usage:user-1:2026-06-16": "1"
  });

  const response = await handleTranscribeRequest(
    makeRequest(),
    {
      FIREBASE_PROJECT_ID: "clipscribe-e3668",
      FREE_TIER_DAILY_TRANSCRIPT_LIMIT: "3",
      __now: new Date("2026-06-16T12:00:00Z"),
      TRANSCRIPTION_PROVIDER: "mock",
      MOCK_TRANSCRIPTION_TEXT: "count me",
      USAGE_KV: usageKv,
      async __verifyFirebaseIdToken() {
        return { sub: "user-1" };
      }
    }
  );

  assert.equal(response.status, 200);
  assert.equal(await response.text(), "count me");
  assert.equal(response.headers.get("X-ClipScribe-Free-Limit"), "3");
  assert.equal(response.headers.get("X-ClipScribe-Free-Used"), "2");
  assert.equal(response.headers.get("X-ClipScribe-Free-Remaining"), "1");
  assert.equal(usageKv.snapshot().get("usage:user-1:2026-06-16"), "2");
});
