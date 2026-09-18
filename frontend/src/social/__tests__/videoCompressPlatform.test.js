// 웹용과 기기용이 어긋나지 않는가.
//
// 🔴 이 갈라치기가 실제로 앱을 깨뜨렸다. 줄이는 꾸러미는 기기 전용이라 웹 번들에 들어가면
//    **웹 앱 전체가 안 뜬다.** 시험도 타입도 안 잡았고 브라우저에서 열어 봐야 알았다.
//
// 재발 경로는 하나다 — **기기용에 내보내는 이름을 하나 더 만들고 웹용에 안 만드는 것.**
// 그러면 웹에서 그 이름이 undefined 가 되고, 크기 비교 같은 것은 조용히 꺼진다.
//
// 🔴 **확장자를 붙여 부른다.** 그냥 '../videoCompress' 라고 쓰면 이 시험 도구가 기기용을
//    골라서, 두 파일을 같은 것으로 읽고 **자기 자신과 비교하며 통과한다.** 실제로 그랬다.
//
// 타입스크립트가 아니라 평범한 자바스크립트인 이유는 확장자를 붙인 들여오기를 타입이
// 거부하기 때문이다 — noControlCharacters·assistantMenu 와 같은 이유다.
jest.mock('react-native-compressor', () => ({ Video: { compress: jest.fn() } }));

const web = require('../videoCompress.ts');
const native = require('../videoCompress.native.ts');

describe('동영상 줄이기 — 웹과 기기', () => {
  it('두 파일을 정말 따로 읽었다 — 같은 것을 두 번 읽으면 아래가 다 무의미하다', () => {
    expect(web).not.toBe(native);
    expect(typeof web.compressForUpload).toBe('function');
    expect(typeof native.compressForUpload).toBe('function');
  });

  it('🔴 내보내는 이름이 같다 — 한쪽에만 생기면 다른 쪽에서 undefined 가 된다', () => {
    expect(Object.keys(web).sort()).toEqual(Object.keys(native).sort());
  });

  it('상한 값도 같다 — 두 벌이 되면 기기에서만 다르게 막힌다', () => {
    expect(web.MAX_VIDEO_UPLOAD_BYTES).toBe(native.MAX_VIDEO_UPLOAD_BYTES);
    expect(web.MAX_VIDEO_SECONDS).toBe(native.MAX_VIDEO_SECONDS);
  });

  it('🔴 웹은 조용히 원본을 돌려주지 않는다 — 실패로 알린다', async () => {
    // 원본을 그대로 돌려주면 「줄였다」로 올라가고, 서버가 거절할 때까지 아무도 모른다.
    await expect(web.compressForUpload('file://a.mp4')).rejects.toThrow();
  });
});
