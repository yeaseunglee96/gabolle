import "dotenv/config";
import { Kafka, logLevel } from "kafkajs";
import { prisma } from "../db.js";

const brokers = (process.env.KAFKA_BROKERS ?? "").split(",").map((value) => value.trim()).filter(Boolean);
if (brokers.length === 0) throw new Error("KAFKA_BROKERS가 설정되지 않았습니다. 예: localhost:9092");

const kafka = new Kafka({ clientId: "local-route-outbox", brokers, logLevel: logLevel.WARN });
const producer = kafka.producer({ idempotent: true, maxInFlightRequests: 1, retry: { retries: 8 } });

async function dispatchBatch(): Promise<number> {
  const rows = await prisma.eventOutbox.findMany({ where: { publishStatus: { in: ["PENDING", "FAILED"] }, publishAttempts: { lt: 10 } }, orderBy: { receivedAt: "asc" }, take: 100 });
  for (const row of rows) {
    try {
      await producer.send({ topic: row.topic, acks: -1, messages: [{ key: row.partitionKey, value: JSON.stringify({ event_id: row.eventId, event: row.eventType, user_id: row.actorId, occurred_at: row.occurredAt.toISOString(), entity_type: row.entityType, entity_id: row.entityId, lang: row.language, payload: JSON.parse(row.payloadJson) }) }] });
      await prisma.eventOutbox.update({ where: { id: row.id }, data: { publishStatus: "PUBLISHED", publishedAt: new Date(), publishAttempts: { increment: 1 }, lastError: null } });
    } catch (error) {
      await prisma.eventOutbox.update({ where: { id: row.id }, data: { publishStatus: "FAILED", publishAttempts: { increment: 1 }, lastError: error instanceof Error ? error.message.slice(0, 1000) : "unknown" } });
    }
  }
  return rows.length;
}

async function main() {
  await producer.connect();
  const once = process.argv.includes("--once");
  do {
    const count = await dispatchBatch();
    if (once) break;
    await new Promise((resolve) => setTimeout(resolve, count > 0 ? 250 : 1000));
  } while (true);
}

async function shutdown() {
  await producer.disconnect();
  await prisma.$disconnect();
  process.exit(0);
}
process.on("SIGINT", () => void shutdown());
process.on("SIGTERM", () => void shutdown());
void main().catch(async (error) => { console.error(error); await shutdown(); });
