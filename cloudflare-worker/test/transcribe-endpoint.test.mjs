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

test("returns 401 when bearer token is missing", async () => {
  const response = await handleTranscribeRequest(
    makeRequest({
      headers: {
        Authorization: ""
      }
    }),
    { TRANSCRIPTION_PROVIDER: "mock", MOCK_TRANSCRIPTION_TEXT: "ignored" }
  );

  assert.equal(response.status, 401);
  assert.equal(await response.text(), "Missing bearer token.");
});

test("returns 200 and transcript text for mock provider", async () => {
  const response = await handleTranscribeRequest(
    makeRequest(),
    { TRANSCRIPTION_PROVIDER: "mock", MOCK_TRANSCRIPTION_TEXT: "Transcript from worker." }
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
    { TRANSCRIPTION_PROVIDER: "mock", MOCK_TRANSCRIPTION_TEXT: "ignored" }
  );

  assert.equal(response.status, 415);
  assert.equal(await response.text(), "Expected Content-Type audio/wav.");
});

test("worker routes transcribe path and rejects unknown paths", async () => {
  const okResponse = await worker.fetch(makeRequest(), {
    TRANSCRIPTION_PROVIDER: "mock",
    MOCK_TRANSCRIPTION_TEXT: "OK"
  });
  assert.equal(okResponse.status, 200);

  const notFoundResponse = await worker.fetch(
    new Request("https://example.com/other"),
    { TRANSCRIPTION_PROVIDER: "mock" }
  );
  assert.equal(notFoundResponse.status, 404);
});
