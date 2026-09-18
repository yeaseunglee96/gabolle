// — iOS 에서 읽어주기가 무음이던 것.
jest.mock('expo-speech', () => ({ speak: jest.fn(), stop: jest.fn() }));
jest.mock('expo-audio', () => ({ setAudioModeAsync: jest.fn() }));

type SpeechMock = { speak: jest.Mock; stop: jest.Mock };
type AudioMock = { setAudioModeAsync: jest.Mock };

function load() {
  jest.resetModules();
  const speech = require('expo-speech') as SpeechMock;
  const audio = require('expo-audio') as AudioMock;
  speech.speak.mockReset();
  speech.stop.mockReset();
  audio.setAudioModeAsync.mockReset();
  const module = require('../speakAloud') as typeof import('../speakAloud');
  return { speech, audio, ...module };
}

// 약속 사슬이 풀릴 때까지 기다린다 — 세션을 잡은 뒤에 읽기 때문이다.
const settle = () => new Promise<void>((resolve) => setImmediate(resolve));

describe('speakAloud', () => {
  it('오디오 세션을 잡은 뒤에 읽는다 — 무음 스위치를 켜 둬도 들려야 하는 기능이다', async () => {
    const { speech, audio, speakAloud } = load();
    audio.setAudioModeAsync.mockResolvedValue(undefined);

    speakAloud('안녕하세요', { language: 'ko-KR' });
    expect(speech.speak).not.toHaveBeenCalled(); // 세션이 먼저다
    await settle();

    expect(audio.setAudioModeAsync).toHaveBeenCalledWith(
      expect.objectContaining({ playsInSilentMode: true, interruptionMode: 'duckOthers' }),
    );
    expect(speech.speak).toHaveBeenCalledWith('안녕하세요', { language: 'ko-KR' });
  });

  it('세션을 잡지 못해도 읽기는 시도한다', async () => {
    const { speech, audio, speakAloud } = load();
    audio.setAudioModeAsync.mockRejectedValue(new Error('no session'));

    speakAloud('화장실이 어디예요?', { language: 'ko-KR' });
    await settle();

    expect(speech.speak).toHaveBeenCalled();
  });

  it('네이티브가 약속이 아닌 것을 돌려줘도 읽는다', async () => {
    const { speech, audio, speakAloud } = load();
    audio.setAudioModeAsync.mockReturnValue(undefined);

    speakAloud('얼음 빼주세요', { language: 'ko-KR' });
    await settle();

    expect(speech.speak).toHaveBeenCalled();
  });

  it('여러 번 읽어도 세션은 한 번만 잡는다', async () => {
    const { speech, audio, speakAloud } = load();
    audio.setAudioModeAsync.mockResolvedValue(undefined);

    speakAloud('하나', { language: 'ko-KR' });
    speakAloud('둘', { language: 'ko-KR' });
    await settle();

    expect(audio.setAudioModeAsync).toHaveBeenCalledTimes(1);
    expect(speech.speak).toHaveBeenCalledTimes(2);
  });

  it('멈추기는 그대로 기기 음성을 멈춘다', () => {
    const { speech, stopSpeaking } = load();
    stopSpeaking();
    expect(speech.stop).toHaveBeenCalled();
  });
});
