import { createHash, randomUUID } from "node:crypto";
import { Prisma } from "@prisma/client";
import { prisma } from "../db.js";

export const EVENT_TOPIC = {
  trip_searched: "trip-events",
  itinerary_generated: "trip-events",
  itinerary_confirmed: "trip-events",
  itinerary_shared: "trip-events",
  trip_completed: "trip-events",
  place_impression: "place-interaction-events",
  place_clicked: "place-interaction-events",
  place_saved: "place-interaction-events",
  place_excluded: "place-interaction-events",
  place_pinned: "place-interaction-events",
  place_reordered: "place-interaction-events",
  directions_started: "place-interaction-events",
  place_visit_verified: "local-events",
  place_revisited: "local-events",
  local_place_recommended: "local-events",
  local_course_created: "local-events",
  local_course_used: "local-events",
  recommendation_satisfaction: "recommendation-events",
  budget_exceeded: "recommendation-events",
  route_guide_viewed: "nav-events",
  taxi_card_generated: "nav-events",
  translation_corrected: "translation-events",
  ad_impression: "ad-events",
  ad_clicked: "ad-events",
  coupon_saved: "ad-events",
  booking_started: "booking-events",
  booking_completed: "booking-events",
  booking_cancelled: "booking-events",
  festival_impression: "trip-events",
  festival_added: "trip-events",
  souvenir_layer_viewed: "place-interaction-events",
  activity_layer_viewed: "place-interaction-events",
  nature_spot_layer_viewed: "place-interaction-events",
  walk_trail_layer_viewed: "place-interaction-events",
  night_view_layer_viewed: "place-interaction-events",
  share_created: "collab-events",
  share_viewed: "collab-events",
  itinerary_cloned: "collab-events",
  companion_invited: "collab-events",
  itinerary_edited: "collab-events",
  story_created: "social-events",
  story_updated: "social-events",
  story_deleted: "social-events",
  story_reported: "social-events",
  user_followed: "social-events",
  user_unfollowed: "social-events",
  weather_warning_viewed: "trip-events",
  // 페이스 러닝
  itinerary_progress_recorded: "place-interaction-events",
  itinerary_replanned_midtrip: "trip-events",
  rhythm_profile_applied: "recommendation-events",
} as const;

export type EventType = keyof typeof EVENT_TOPIC;
export const EVENT_TYPES = Object.freeze(Object.keys(EVENT_TOPIC) as EventType[]);

export function isEventType(value: string): value is EventType {
  return Object.hasOwn(EVENT_TOPIC, value);
}

export function pseudonymize(value: string): string {
  return createHash("sha256").update(value).digest("hex").slice(0, 24);
}

const SENSITIVE_KEYS = new Set(["latitude", "longitude", "lat", "lng", "token", "email", "phone", "cardNumber", "receiptImage"]);
export function sanitizeEventPayload(value: unknown): unknown {
  if (Array.isArray(value)) return value.slice(0, 100).map(sanitizeEventPayload);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>)
      .filter(([key]) => !SENSITIVE_KEYS.has(key))
      .slice(0, 50)
      .map(([key, child]) => [key, sanitizeEventPayload(child)]));
  }
  if (typeof value === "string") return value.slice(0, 1000);
  return value;
}

/**
 * 조회 경로 전용 - 이벤트 기록 실패가 멀쩡한 응답을 망치지 않게 한다.
 *
 * GET 핸들러의 관찰용 이벤트(route_guide_viewed 등)는 사용자에게 돌려줄 결과가
 * 이미 준비된 뒤에 기록된다. 여기서 던지면 성공한 조회가 500 으로 뒤바뀌므로,
 * 실패는 경고 로그로만 남기고 삼킨다.
 *
 * 상태 변경을 기록하는 이벤트에는 쓰지 말 것. 그쪽은 이벤트 유실이 드러나야 하고,
 * 웹훅처럼 호출자가 재시도해 복구할 수 있는 경로도 있다.
 */
export async function recordEventBestEffort(input: Parameters<typeof recordEvent>[0]) {
  try {
    return await recordEvent(input);
  } catch (error) {
    console.warn(`[events] 조회 경로 이벤트 기록 실패 (${input.eventType}):`, error instanceof Error ? error.message : error);
    return null;
  }
}

export async function recordEvent(input: {
  eventId?: string;
  eventType: EventType;
  actorId?: string | null;
  entityType?: string | null;
  entityId?: string | null;
  language?: string | null;
  payload?: unknown;
  occurredAt?: Date;
}) {
  const eventId = input.eventId ?? randomUUID();
  const topic = EVENT_TOPIC[input.eventType];
  const partitionKey = input.entityId ?? input.actorId ?? eventId;
  try {
    const event = await prisma.eventOutbox.create({ data: {
      eventId,
      eventType: input.eventType,
      topic,
      partitionKey,
      actorId: input.actorId,
      entityType: input.entityType,
      entityId: input.entityId,
      language: input.language,
      payloadJson: JSON.stringify(sanitizeEventPayload(input.payload ?? {})),
      occurredAt: input.occurredAt ?? new Date(),
      publishStatus: process.env.KAFKA_BROKERS ? "PENDING" : "LOCAL_COMMITTED",
      publishedAt: process.env.KAFKA_BROKERS ? null : new Date(),
    } });
    return { event, deduplicated: false };
  } catch (error) {
    if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") {
      const event = await prisma.eventOutbox.findUniqueOrThrow({ where: { eventId } });
      return { event, deduplicated: true };
    }
    throw error;
  }
}
