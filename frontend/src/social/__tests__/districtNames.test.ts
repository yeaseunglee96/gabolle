// 기록의 동네 이름 영어판 — 「23h ago · 영도구」처럼 영어판에서도 한국어였다(S15P21E201-1707, 조율 세션 결정 E).
//
// 🔴 기준(조율 세션):
//    - 부산 16개 구·군 이름과 **글자 그대로 똑같을 때만** 영어로 보인다(예: Haeundae-gu). 그 밖의 글자는 그대로.
//    - 저장된 값은 바꾸지 않는다. 보여 줄 때만 바꾸고, 걸러 보기는 원래 값으로 비교한다.
import { BUSAN_DISTRICTS_EN, regionText } from '../districtNames';

// tsconfig 가 node 타입을 안 들고 있어서 import 로 쓰면 타입 검사가 막힌다 — 이 저장소의 다른 파일 검사 시험과 같은 방식.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');

const tx = (ko: string) => ko;
const txEn = (_ko: string, en: string) => en;

describe('부산 구·군 이름을 영어로', () => {
  it('열여섯 곳 — 부산광역시의 구 15 · 군 1', () => {
    expect(BUSAN_DISTRICTS_EN).toEqual({
      중구: 'Jung-gu', 서구: 'Seo-gu', 동구: 'Dong-gu', 영도구: 'Yeongdo-gu', 부산진구: 'Busanjin-gu', 동래구: 'Dongnae-gu',
      남구: 'Nam-gu', 북구: 'Buk-gu', 해운대구: 'Haeundae-gu', 사하구: 'Saha-gu', 금정구: 'Geumjeong-gu', 강서구: 'Gangseo-gu',
      연제구: 'Yeonje-gu', 수영구: 'Suyeong-gu', 사상구: 'Sasang-gu', 기장군: 'Gijang-gun',
    });
  });

  it('영어판은 영어, 한국어판은 그대로', () => {
    expect(regionText('해운대구', txEn)).toBe('Haeundae-gu');
    expect(regionText('기장군', txEn)).toBe('Gijang-gun');
    expect(regionText('해운대구', tx)).toBe('해운대구');
  });

  it.each(['해운대', '부산 해운대구', '해운대구 우동', ' 해운대구', '영도'])('🔴 글자 그대로가 아니면 그대로 — 「%s」', (region) => {
    expect(regionText(region, txEn)).toBe(region);
  });

  // 「장소 · 구」 묶음은 우리 묶음 기호 « · » 로만 나눠 마디마다 글자 그대로 맞춘다(S15P21E201-1912).
  it('「광안리해수욕장 · 수영구」 — 구 마디만 바뀌고 장소 마디는 그대로', () => {
    expect(regionText('광안리해수욕장 · 수영구', txEn)).toBe('광안리해수욕장 · Suyeong-gu');
  });

  it.each([
    ['app/(tabs)/feed.tsx', 2],
    ['app/feed/[id].tsx', 2],
    ['src/home/HomeBlocks.tsx', 1],
    ['src/me/RecordCard.tsx', 1],
    ['src/me/panels/MyPostsBody.tsx', 1],
    ['src/me/panels/SavedRecordsBody.tsx', 1],
  ])('%s — 동네 이름을 그리는 %i 곳이 모두 regionText 를 거친다', (file, count) => {
    const source = readFileSync(join(__dirname, '..', '..', '..', file), 'utf8') as string;
    // placeRegion — 장소 제목 아래 줄은 앞의 장소 이름을 뗀 값을 거친다(S15P21E201-1759).
    expect(source.match(/regionText\((?:story\.region|placeRegion), tx[,)]/g) ?? []).toHaveLength(count);
  });

  it('마이페이지 걸러 보기 칩 — 글자만 바꾸고, 고르는 값은 원래 글자', () => {
    const source = readFileSync(join(__dirname, '..', '..', '..', 'src/me/RecordsBrowser.tsx'), 'utf8') as string;
    expect(source).toContain('label={regionText(region, tx)}');
    expect(source).toContain('region: on ? null : region }))');
  });
});
