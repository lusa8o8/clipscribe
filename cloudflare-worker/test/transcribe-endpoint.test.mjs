import test from "node:test";
import assert from "node:assert/strict";
import worker, {
  handleTranscribeRequest,
  handleTranscriptPersistenceRequest
} from "../src/index.mjs";

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

function makeTranscriptRequest(path = "/transcripts", overrides = {}) {
  return new Request(`https://example.com${path}`, {
    method: overrides.method || "GET",
    headers: {
      Authorization: "Bearer firebase-token",
      ...(overrides.body == null ? {} : { "Content-Type": "application/json" }),
      ...overrides.headers
    },
    body: overrides.body == null ? undefined : JSON.stringify(overrides.body)
  });
}

function makeMemoryTranscriptDb(initialRows = []) {
  const rows = [...initialRows];

  return {
    rows,
    prepare(sql) {
      const statement = {
        bindings: [],
        bind(...values) {
          statement.bindings = values;
          return statement;
        },
        async run() {
          if (/INSERT INTO transcripts/i.test(sql)) {
            const [id, uid, text, sourceDurationSeconds, createdAt] = statement.bindings;
            rows.push({
              id,
              uid,
              text,
              source_duration_seconds: sourceDurationSeconds,
              created_at: createdAt
            });
          } else if (/DELETE FROM transcripts/i.test(sql)) {
            const [uid, id] = statement.bindings;
            const index = rows.findIndex((row) => row.uid === uid && row.id === id);
            if (index >= 0) {
              rows.splice(index, 1);
            }
          }
          return { success: true };
        },
        async all() {
          const [uid] = statement.bindings;
          return {
            results: rows
              .filter((row) => row.uid === uid)
              .sort((left, right) => right.created_at.localeCompare(left.created_at))
          };
        }
      };
      return statement;
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
    "Free transcript limit reached for today. Try again tomorrow."
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

test("returns 401 when saving transcript without bearer token", async () => {
  const response = await handleTranscriptPersistenceRequest(
    makeTranscriptRequest("/transcripts", {
      method: "POST",
      headers: { Authorization: "" },
      body: { text: "ignored", sourceDurationSeconds: 12 }
    }),
    {
      FIREBASE_PROJECT_ID: "clipscribe-e3668",
      TRANSCRIPTS_DB: makeMemoryTranscriptDb()
    }
  );

  assert.equal(response.status, 401);
  assert.equal(await response.text(), "Missing bearer token.");
});

test("saves transcript for verified user", async () => {
  const db = makeMemoryTranscriptDb();
  const response = await handleTranscriptPersistenceRequest(
    makeTranscriptRequest("/transcripts", {
      method: "POST",
      body: { text: "Useful transcript", sourceDurationSeconds: 18.6 }
    }),
    {
      FIREBASE_PROJECT_ID: "clipscribe-e3668",
      TRANSCRIPTS_DB: db,
      async __verifyFirebaseIdToken() {
        return { sub: "user-1" };
      }
    }
  );

  assert.equal(response.status, 201);
  const body = await response.json();
  assert.equal(body.transcript.text, "Useful transcript");
  assert.equal(body.transcript.sourceDurationSeconds, 19);
  assert.equal(db.rows.length, 1);
  assert.equal(db.rows[0].uid, "user-1");
});

test("lists only transcripts owned by verified user", async () => {
  const db = makeMemoryTranscriptDb([
    {
      id: "own-old",
      uid: "user-1",
      text: "old",
      source_duration_seconds: 10,
      created_at: "2026-06-16T10:00:00.000Z"
    },
    {
      id: "other",
      uid: "user-2",
      text: "other user",
      source_duration_seconds: 30,
      created_at: "2026-06-16T12:00:00.000Z"
    },
    {
      id: "own-new",
      uid: "user-1",
      text: "new",
      source_duration_seconds: 20,
      created_at: "2026-06-16T13:00:00.000Z"
    }
  ]);

  const response = await handleTranscriptPersistenceRequest(
    makeTranscriptRequest(),
    {
      FIREBASE_PROJECT_ID: "clipscribe-e3668",
      TRANSCRIPTS_DB: db,
      async __verifyFirebaseIdToken() {
        return { sub: "user-1" };
      }
    }
  );

  assert.equal(response.status, 200);
  const body = await response.json();
  assert.deepEqual(
    body.transcripts.map((transcript) => transcript.id),
    ["own-new", "own-old"]
  );
});

test("delete transcript is scoped to verified user", async () => {
  const db = makeMemoryTranscriptDb([
    {
      id: "shared-id",
      uid: "user-1",
      text: "mine",
      source_duration_seconds: 10,
      created_at: "2026-06-16T10:00:00.000Z"
    },
    {
      id: "shared-id",
      uid: "user-2",
      text: "not mine",
      source_duration_seconds: 10,
      created_at: "2026-06-16T10:00:00.000Z"
    }
  ]);

  const response = await handleTranscriptPersistenceRequest(
    makeTranscriptRequest("/transcripts/shared-id", { method: "DELETE" }),
    {
      FIREBASE_PROJECT_ID: "clipscribe-e3668",
      TRANSCRIPTS_DB: db,
      async __verifyFirebaseIdToken() {
        return { sub: "user-1" };
      }
    },
    "shared-id"
  );

  assert.equal(response.status, 204);
  assert.deepEqual(db.rows.map((row) => row.uid), ["user-2"]);
});
