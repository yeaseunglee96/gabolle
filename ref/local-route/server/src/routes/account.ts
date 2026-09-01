import { Router } from "express";
import { Prisma } from "@prisma/client";
import { prisma } from "../db.js";
import { hashSessionToken, issueSessionToken } from "../services/community.js";
import { optionalSession, sessionToken } from "../services/auth.js";
import { createPasswordRecord, normalizeEmail, PASSWORD_RULE, validEmail, verifyPassword } from "../services/account.js";

export const accountRouter = Router();
const accountLifetimeMs = 30 * 24 * 60 * 60 * 1000;
const oauthProviders = new Set(["GOOGLE", "NAVER", "KAKAO"]);
const consentCategories = ["BEHAVIOR", "SENSITIVE", "PRECISE_LOCATION"] as const;

function publicUser(user: { id: string; email: string; name: string; emailVerified: boolean; locale: string; nationality: string | null; dietType: string; allergies: string; travelStyle: string; defaultTransport: string; avatarImage: string | null; avatarColor: string }) {
  let allergies: string[] = [];
  try { allergies = JSON.parse(user.allergies); } catch { allergies = []; }
  return { id: user.id, email: user.email, name: user.name, emailVerified: user.emailVerified, locale: user.locale, nationality: user.nationality, dietType: user.dietType, allergies, travelStyle: user.travelStyle, defaultTransport: user.defaultTransport, avatarImage: user.avatarImage, avatarColor: user.avatarColor };
}

const allowed = {
  locale: new Set(["KO", "EN"]),
  dietType: new Set(["NONE", "VEGETARIAN", "VEGAN", "HALAL"]),
  travelStyle: new Set(["RELAXED", "BALANCED", "PACKED"]),
  defaultTransport: new Set(["TRANSIT", "CAR", "WALK"]),
  avatarColor: new Set(["LAVENDER", "SKY", "MINT", "PEACH", "CHARCOAL"]),
};

async function currentAccount(req: Parameters<typeof sessionToken>[0]) {
  const token = sessionToken(req);
  if (!token) return null;
  const account = await prisma.accountSession.findUnique({ where: { tokenHash: hashSessionToken(token) }, include: { user: true } });
  return account && account.expiresAt > new Date() ? account : null;
}

accountRouter.post("/auth/register", async (req, res, next) => {
  try {
    const email = normalizeEmail(req.body?.email);
    const name = typeof req.body?.name === "string" ? req.body.name.trim().slice(0, 50) : "";
    const password = typeof req.body?.password === "string" ? req.body.password : "";
    const dateOfBirth = typeof req.body?.dateOfBirth === "string" ? req.body.dateOfBirth : "";
    const born = /^\d{4}-\d{2}-\d{2}$/.test(dateOfBirth) ? new Date(`${dateOfBirth}T00:00:00Z`) : null;
    const today = new Date();
    let age = born ? today.getUTCFullYear() - born.getUTCFullYear() : -1;
    if (born) {
      const month = today.getUTCMonth() - born.getUTCMonth();
      if (month < 0 || (month === 0 && today.getUTCDate() < born.getUTCDate())) age -= 1;
    }
    if (!name) return res.status(400).json({ error_code: "NAME_REQUIRED", message: "이름을 입력해주세요." });
    if (!validEmail(email)) return res.status(400).json({ error_code: "INVALID_EMAIL", message: "이메일 주소를 확인해주세요." });
    if (!PASSWORD_RULE.test(password)) return res.status(400).json({ error_code: "WEAK_PASSWORD", message: "비밀번호는 8~64자이며 영문, 숫자, 특수문자를 포함해야 합니다." });
    if (age < 14) return res.status(403).json({ error_code: "AGE_RESTRICTED", message: "만 14세 이상만 가입할 수 있습니다." });

    const guest = await optionalSession(req);
    const credentials = await createPasswordRecord(password);
    const issued = issueSessionToken();
    const expiresAt = new Date(Date.now() + accountLifetimeMs);
    const result = await prisma.$transaction(async (tx) => {
      const ownerSession = guest ?? await tx.anonymousSession.create({ data: { tokenHash: issueSessionToken().tokenHash, locale: req.body?.locale === "EN" ? "EN" : "KO", expiresAt, localProfile: { create: {} } } });
      const user = await tx.user.create({ data: { email, name, passwordHash: credentials.hash, passwordSalt: credentials.salt, ownerSessionId: ownerSession.id, locale: req.body?.locale === "EN" ? "EN" : "KO" } });
      await tx.accountSession.create({ data: { tokenHash: issued.tokenHash, userId: user.id, expiresAt } });
      return user;
    });
    res.status(201).json({ token: issued.token, user: publicUser(result) });
  } catch (error) {
    if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") return res.status(409).json({ error_code: "EMAIL_ALREADY_EXISTS", message: "이미 가입된 이메일입니다." });
    next(error);
  }
});

accountRouter.post("/auth/login", async (req, res, next) => {
  try {
    const email = normalizeEmail(req.body?.email);
    const password = typeof req.body?.password === "string" ? req.body.password : "";
    const user = await prisma.user.findUnique({ where: { email } });
    if (!user || !await verifyPassword(password, user.passwordSalt, user.passwordHash)) return res.status(401).json({ error_code: "INVALID_CREDENTIALS", message: "이메일 또는 비밀번호가 올바르지 않습니다." });
    const issued = issueSessionToken();
    await prisma.accountSession.create({ data: { tokenHash: issued.tokenHash, userId: user.id, expiresAt: new Date(Date.now() + accountLifetimeMs) } });
    res.json({ token: issued.token, user: publicUser(user) });
  } catch (error) { next(error); }
});

accountRouter.get("/auth/providers", (_req, res) => {
  res.json({ providers: Array.from(oauthProviders) });
});

accountRouter.post("/auth/oauth/demo", async (req, res, next) => {
  try {
    if (process.env.NODE_ENV === "production") return res.status(404).json({ error_code: "NOT_FOUND" });
    const provider = String(req.body?.provider ?? "").toUpperCase();
    if (!oauthProviders.has(provider)) return res.status(400).json({ error_code: "UNSUPPORTED_PROVIDER", message: "지원하지 않는 로그인 제공자입니다." });
    const locale = req.body?.locale === "EN" ? "EN" : "KO";
    const email = `${provider.toLowerCase()}.demo@gabolle.local`;
    let user = await prisma.user.findUnique({ where: { email } });
    if (!user) {
      const guest = await optionalSession(req);
      const expiresAt = new Date(Date.now() + accountLifetimeMs);
      const ownerSession = guest ?? await prisma.anonymousSession.create({ data: { tokenHash: issueSessionToken().tokenHash, locale, expiresAt, localProfile: { create: {} } } });
      const credentials = await createPasswordRecord(issueSessionToken().token);
      user = await prisma.user.create({ data: { email, name: `${provider[0]}${provider.slice(1).toLowerCase()} 여행자`, passwordHash: credentials.hash, passwordSalt: credentials.salt, emailVerified: true, ownerSessionId: ownerSession.id, locale } });
    }
    const issued = issueSessionToken();
    await prisma.accountSession.create({ data: { tokenHash: issued.tokenHash, userId: user.id, expiresAt: new Date(Date.now() + accountLifetimeMs) } });
    res.json({ token: issued.token, user: publicUser(user), demo: true, provider });
  } catch (error) { next(error); }
});

accountRouter.get("/auth/me", async (req, res, next) => {
  try {
    const account = await currentAccount(req);
    if (!account) return res.status(401).json({ error_code: "SESSION_EXPIRED", message: "로그인이 필요하거나 만료되었습니다." });
    res.json({ user: publicUser(account.user) });
  } catch (error) { next(error); }
});

accountRouter.patch("/auth/me", async (req, res, next) => {
  try {
    const account = await currentAccount(req);
    if (!account) return res.status(401).json({ error_code: "SESSION_EXPIRED", message: "로그인이 필요하거나 만료되었습니다." });
    const name = typeof req.body?.name === "string" ? req.body.name.trim().slice(0, 50) : "";
    const nationality = typeof req.body?.nationality === "string" ? req.body.nationality.trim().slice(0, 50) || null : null;
    const allergies = Array.isArray(req.body?.allergies) ? req.body.allergies.filter((item: unknown): item is string => typeof item === "string").map((item: string) => item.trim()).filter(Boolean).slice(0, 10) : [];
    const avatarImage = req.body?.avatarImage === null ? null : typeof req.body?.avatarImage === "string" && /^data:image\/(jpeg|png|webp);base64,/.test(req.body.avatarImage) && req.body.avatarImage.length <= 190_000 ? req.body.avatarImage : null;
    if (!name) return res.status(400).json({ error_code: "NAME_REQUIRED", message: "이름을 입력해주세요." });
    if (!allowed.locale.has(req.body?.locale) || !allowed.dietType.has(req.body?.dietType) || !allowed.travelStyle.has(req.body?.travelStyle) || !allowed.defaultTransport.has(req.body?.defaultTransport) || !allowed.avatarColor.has(req.body?.avatarColor)) {
      return res.status(400).json({ error_code: "INVALID_PROFILE", message: "프로필 설정값을 확인해주세요." });
    }
    const user = await prisma.user.update({ where: { id: account.userId }, data: { name, nationality, locale: req.body.locale, dietType: req.body.dietType, allergies: JSON.stringify(allergies), travelStyle: req.body.travelStyle, defaultTransport: req.body.defaultTransport, avatarImage, avatarColor: req.body.avatarColor } });
    res.json({ user: publicUser(user) });
  } catch (error) { next(error); }
});

accountRouter.get("/auth/me/consents", async (req, res, next) => {
  try {
    const account = await currentAccount(req);
    if (!account) return res.status(401).json({ error_code: "SESSION_EXPIRED", message: "로그인이 필요하거나 만료되었습니다." });
    const rows = await prisma.userConsent.findMany({ where: { userId: account.userId } });
    const granted = new Map(rows.map((row) => [row.category, row.granted]));
    const policyVersion = rows[0]?.policyVersion ?? "2026-09";
    const behavior = granted.get("BEHAVIOR") ?? false;
    res.json({ personalizationMode: behavior ? "BEHAVIOR_ENABLED" : "EXPLICIT_ONLY", behavior, sensitive: granted.get("SENSITIVE") ?? false, preciseLocation: granted.get("PRECISE_LOCATION") ?? false, policyVersion });
  } catch (error) { next(error); }
});

accountRouter.put("/auth/me/consents", async (req, res, next) => {
  try {
    const account = await currentAccount(req);
    if (!account) return res.status(401).json({ error_code: "SESSION_EXPIRED", message: "로그인이 필요하거나 만료되었습니다." });
    const policyVersion = typeof req.body?.policyVersion === "string" ? req.body.policyVersion.slice(0, 30) : "2026-09";
    const values = { BEHAVIOR: req.body?.behavior === true, SENSITIVE: req.body?.sensitive === true, PRECISE_LOCATION: req.body?.preciseLocation === true };
    await prisma.$transaction(consentCategories.map((category) => prisma.userConsent.upsert({ where: { userId_category: { userId: account.userId, category } }, create: { userId: account.userId, category, granted: values[category], policyVersion }, update: { granted: values[category], policyVersion } })));
    res.json({ personalizationMode: values.BEHAVIOR ? "BEHAVIOR_ENABLED" : "EXPLICIT_ONLY", behavior: values.BEHAVIOR, sensitive: values.SENSITIVE, preciseLocation: values.PRECISE_LOCATION, policyVersion });
  } catch (error) { next(error); }
});

accountRouter.post("/auth/logout", async (req, res, next) => {
  try {
    const token = sessionToken(req);
    if (token) {
      await prisma.accountSession.deleteMany({ where: { tokenHash: hashSessionToken(token) } });
    }
    res.status(204).end();
  } catch (error) { next(error); }
});
