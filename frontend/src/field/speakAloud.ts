// 기기 음성으로 소리 내어 읽기 —.
import * as Speech from 'expo-speech';
import { setAudioModeAsync } from 'expo-audio';

/**
 * 오디오 세션은 한 번만 잡으면 된다. 실패해도 재생은 시도한다
 * 세션 설정이 안 됐다고 안 읽어 주는 것보다, 읽어 보고 안 들리는 편이 낫다.
 */
let audioSession: Promise<void> | null = null;

function ensureAudioSession(): Promise<void> {
  if (!audioSession) {
    // Promise.resolve 로 감싼다 — 네이티브 모듈이 약속이 아닌 것을 돌려줘도 읽기가 멈추지 않게.
    audioSession = Promise.resolve(setAudioModeAsync({
      // 무음 스위치를 켜 둔 채 식당에서 문장을 들려주려는 사람이 이 기능의 주인이다.
      // 여기서 소리가 안 나면 기능 자체가 없는 것과 같다.
      playsInSilentMode: true,
      // 음악을 끄지 않고 잠깐 줄인다. 문장 한 줄 때문에 남의 재생을 끊지 않는다.
      interruptionMode: 'duckOthers',
    })).catch(() => undefined);
  }
  return audioSession;
}

/**
 * 읽어 준다. 언어를 문장에 맞춰 주는 것은 부르는 쪽 몫이다
 * 영어를 한국어 음성으로 읽으면 상대가 못 알아듣는다.
 */
export function speakAloud(text: string, options: Speech.SpeechOptions) {
  void ensureAudioSession().then(() => Speech.speak(text, options));
}

/** 읽던 것을 멈춘다. */
export function stopSpeaking() {
  Speech.stop();
}
