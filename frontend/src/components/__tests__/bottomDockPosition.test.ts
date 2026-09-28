// 화면 아래에 떠 있는 것들의 기준 — S15P21E201-1601.
//
// 🔴 이 시험이 지키는 것: 폰 홈의 「AI에게 물어보기」 버튼이 하단 탭바와 **같은 기준**으로 선다.
//    탭바는 웹에서 보이는 창에 고정(fixed)인데 버튼만 부모 상자 기준(absolute)이면, 폰 크롬에서 주소창이 접혀
//    보이는 창이 길어질 때 탭바만 새 바닥으로 내려가고 버튼은 옛 자리에 남아 간격이 벌어진다(흉내 측정 18px → 74px).
import { bottomDockPosition } from '@/components/TabBar';

// 소스를 읽는 시험은 이 저장소의 방식대로 require 로 부른다 — 앱 tsconfig 에 node 타입이 없다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');
const read = (file: string) => readFileSync(join(__dirname, '..', '..', '..', file), 'utf8') as string;

describe('화면 아래에 떠 있는 것들의 기준', () => {
  it('웹에서는 보이는 창에 고정, 앱에서는 부모 기준', () => {
    expect(bottomDockPosition('web')).toBe('fixed');
    expect(bottomDockPosition('ios')).toBe('absolute');
    expect(bottomDockPosition('android')).toBe('absolute');
  });

  it('🔴 탭바와 그 곁에 뜨는 것(폰 홈 AI 버튼 · 피드 복사 알림)이 같은 기준을 쓴다', () => {
    expect(read('src/components/TabBar.tsx')).toMatch(/dock: \{[\s\S]*?position: bottomDockPosition\(\)/);
    expect(read('app/(tabs)/home.tsx')).toMatch(/assistantAnchor: \{ position: bottomDockPosition\(\)/);
    expect(read('app/(tabs)/feed.tsx')).toMatch(/copyNoticeDock: \{ position: bottomDockPosition\(\)/);
  });
});
