// 기기 음성으로 소리 내어 읽기 — S15P21E201-1126.
//
// 🔴 이 파일이 생긴 이유는 iOS 에서 읽어주기가 아무 소리도 내지 않았기 때문이다.
//    2026-09-16 실기기(iPhone 12 Pro · iOS 26.6.2)에서 현장 말하기의 재생 버튼을
//    몇 번을 눌러도 무음이었다. 무음 스위치는 꺼져 있었고, 오류도 onError 도 없었다.
//    같은 날 같은 코드가 안드로이드(Galaxy S10)에서는 정상으로 소리를 냈다 —
//    dumpsys audio 에 CONTENT_TYPE_SPEECH AudioTrack 이 state:started 로 잡혔다.
//
//    iOS 는 앱이 오디오 세션(이 앱이 소리를 어떤 성격으로 낼 것인지 운영체제에 미리
//    알리는 설정)을 잡지 않으면, 소리를 조용히 건너뛴다. 예외도 콜백도 없다.
//    expo-speech 는 그 설정을 스스로 하지 않는다 — 그래서 여기서 한다.
//
// 🔴 부르는 곳을 한 곳으로 모은 이유도 있다. 전에는 화면 네 곳이 Speech.speak 을
//    제각기 불렀다. 그중 한 곳만 고치면 나머지 세 곳은 계속 무음이다.
import * as Speech from 'expo-speech';
import { setAudioModeAsync } from 'expo-audio';

/**
 * 오디오 세션은 한 번만 잡으면 된다. 실패해도 재생은 시도한다 —
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
 * 읽어 준다. 언어를 문장에 맞춰 주는 것은 부르는 쪽 몫이다 —
 * 영어를 한국어 음성으로 읽으면 상대가 못 알아듣는다.
 */
export function speakAloud(text: string, options: Speech.SpeechOptions) {
  void ensureAudioSession().then(() => Speech.speak(text, options));
}

/** 읽던 것을 멈춘다. */
export function stopSpeaking() {
  Speech.stop();
}
