import test from "node:test";
import assert from "node:assert/strict";
import { createPasswordRecord, normalizeEmail, PASSWORD_RULE, validEmail, verifyPassword } from "../src/services/account.js";

test("account input normalization and password policy reject weak credentials", () => {
  assert.equal(normalizeEmail("  Traveler@Example.COM "), "traveler@example.com");
  assert.equal(validEmail("traveler@example.com"), true);
  assert.equal(validEmail("traveler@"), false);
  assert.equal(PASSWORD_RULE.test("12345678"), false);
  assert.equal(PASSWORD_RULE.test("Route!2026"), true);
});

test("password records use a random salt and verify without storing plaintext", async () => {
  const first = await createPasswordRecord("Route!2026");
  const second = await createPasswordRecord("Route!2026");
  assert.notEqual(first.salt, second.salt);
  assert.notEqual(first.hash, second.hash);
  assert.equal(await verifyPassword("Route!2026", first.salt, first.hash), true);
  assert.equal(await verifyPassword("wrong!2026", first.salt, first.hash), false);
});
