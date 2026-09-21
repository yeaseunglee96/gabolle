// 부산 사투리 목소리 — S15P21E201-1422.
//
// 기기 TTS(expo-speech)는 서울말 엔진이라 「마이 무라」를 표준어 억양으로 읽었다(실기 2026-09-21).
// 사투리 카드 문장은 정해져 있으므로(dialectPhrases.ts) 타입캐스트(Typecast) 경상도 목소리
// 「개나리」(여) · 「용식이」(남)로 미리 만든 mp3 를 앱에 넣는다 — 서버·네트워크 없이, 오프라인에서도 난다.
// 문장이 늘면 scratch 의 gen-dialect.cjs 로 다시 뽑는다(무료 한도 월 15,000자, 9문장×2 ≈ 90자).
// 🔴 무료 플랜 조건 = 출처 표시 → legal/legalContent.ts 「공공데이터 출처」에 한 줄 있다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createAudioPlayer, setAudioModeAsync, type AudioPlayer } from 'expo-audio';

export type DialectVoice = 'FEMALE' | 'MALE';
const STORAGE_KEY = 'gabolle:dialect-voice';

// require 는 문자열을 못 받는다(번들러가 정적으로 읽는다) — 문장마다 손으로 적는다.
const CLIPS: Record<string, { FEMALE: number; MALE: number }> = {
  mworakano: { FEMALE: require('../../assets/audio/dialect/mworakano-f.mp3'), MALE: require('../../assets/audio/dialect/mworakano-m.mp3') },
  dwaetdaaiga: { FEMALE: require('../../assets/audio/dialect/dwaetdaaiga-f.mp3'), MALE: require('../../assets/audio/dialect/dwaetdaaiga-m.mp3') },
  ujjano: { FEMALE: require('../../assets/audio/dialect/ujjano-f.mp3'), MALE: require('../../assets/audio/dialect/ujjano-m.mp3') },
  aida: { FEMALE: require('../../assets/audio/dialect/aida-f.mp3'), MALE: require('../../assets/audio/dialect/aida-m.mp3') },
  haigo: { FEMALE: require('../../assets/audio/dialect/haigo-f.mp3'), MALE: require('../../assets/audio/dialect/haigo-m.mp3') },
  maimura: { FEMALE: require('../../assets/audio/dialect/maimura-f.mp3'), MALE: require('../../assets/audio/dialect/maimura-m.mp3') },
  geukajimara: { FEMALE: require('../../assets/audio/dialect/geukajimara-f.mp3'), MALE: require('../../assets/audio/dialect/geukajimara-m.mp3') },
  eoksuroota: { FEMALE: require('../../assets/audio/dialect/eoksuroota-f.mp3'), MALE: require('../../assets/audio/dialect/eoksuroota-m.mp3') },
};

/** 이 문장에 미리 만든 목소리가 있는가 — 없으면 부르는 쪽이 기기 TTS 로 내려간다. */
export function hasDialectClip(phraseId: string): boolean {
  return phraseId in CLIPS;
}

export async function loadDialectVoice(): Promise<DialectVoice> {
  try {
    const saved = await AsyncStorage.getItem(STORAGE_KEY);
    return saved === 'MALE' ? 'MALE' : 'FEMALE';
  } catch {
    return 'FEMALE';
  }
}

export async function saveDialectVoice(voice: DialectVoice): Promise<void> {
  try { await AsyncStorage.setItem(STORAGE_KEY, voice); } catch { /* 기기에 못 남겨도 이번 재생은 된다 */ }
}

let current: AudioPlayer | null = null;
let audioSession: Promise<void> | null = null;

function ensureAudioSession(): Promise<void> {
  if (!audioSession) {
    // speakAloud.ts 와 같은 이유 — 무음 스위치를 켠 채 식당에서 들려주려는 사람이 주인이다.
    audioSession = Promise.resolve(setAudioModeAsync({ playsInSilentMode: true, interruptionMode: 'duckOthers' })).catch(() => undefined);
  }
  return audioSession;
}

export function stopDialectClip() {
  if (!current) return;
  try { current.pause(); current.remove(); } catch { /* 이미 정리됐다 */ }
  current = null;
}

/**
 * 미리 만든 목소리를 튼다. 끝나거나 실패하면 onDone 을 한 번 부른다.
 * 클립이 없으면 false 를 돌려주고 아무것도 틀지 않는다 — 부르는 쪽이 기기 TTS 로 간다.
 */
export async function playDialectClip(phraseId: string, voice: DialectVoice, onDone: () => void): Promise<boolean> {
  const clip = CLIPS[phraseId]?.[voice];
  if (!clip) return false;
  stopDialectClip();
  await ensureAudioSession();
  let finished = false;
  const finish = () => { if (finished) return; finished = true; if (current === player) { try { player.remove(); } catch { /* */ } current = null; } onDone(); };
  let player: AudioPlayer;
  try {
    player = createAudioPlayer(clip);
  } catch {
    return false;
  }
  current = player;
  player.addListener('playbackStatusUpdate', (status) => { if (status.didJustFinish) finish(); });
  try { player.play(); } catch { finish(); return false; }
  return true;
}
