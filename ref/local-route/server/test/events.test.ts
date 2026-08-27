import test from "node:test";
import assert from "node:assert/strict";
import { EVENT_TOPIC, EVENT_TYPES, isEventType, pseudonymize, sanitizeEventPayload } from "../src/services/events.js";

test("v2 부가 기능 이벤트를 포함해 38개 이상의 이벤트를 토픽에 연결한다", () => {
  assert.ok(EVENT_TYPES.length >= 38);
  for (const event of ["festival_impression", "festival_added", "souvenir_layer_viewed", "share_created", "itinerary_cloned", "companion_invited", "itinerary_edited", "story_created", "story_reported", "user_followed", "weather_warning_viewed"]) assert.ok(EVENT_TYPES.includes(event as any));
  assert.equal(new Set(EVENT_TYPES).size, EVENT_TYPES.length);
  assert.equal(EVENT_TOPIC.booking_cancelled, "booking-events");
  assert.equal(EVENT_TOPIC.translation_corrected, "translation-events");
  assert.equal(isEventType("place_visit_verified"), true);
});

test("이벤트 payload에서 원본 위치와 민감정보를 제거한다", () => {
  const sanitized = sanitizeEventPayload({ latitude: 35.1, longitude: 129.1, placeId: "p1", nested: { token: "secret", safe: true } }) as any;
  assert.equal(sanitized.latitude, undefined);
  assert.equal(sanitized.nested.token, undefined);
  assert.equal(sanitized.placeId, "p1");
  assert.equal(sanitized.nested.safe, true);
});

test("이벤트 행위자 식별자는 가명화한다", () => {
  assert.equal(pseudonymize("same"), pseudonymize("same"));
  assert.notEqual(pseudonymize("same"), pseudonymize("different"));
  assert.equal(pseudonymize("same").length, 24);
});
