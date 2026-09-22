// 홈 머리의 날씨 숫자 — S15P21E201-1502.
//
// 🔴 기상청 단기예보는 남은 시간대가 짧으면 최저와 최고를 같게 준다. 그대로 이어 붙이면
//    「22° / 22°」가 되고, 실기에서 그것을 고장으로 읽었다. 같은 숫자를 두 번 보이지 않는다.
//    원값이 21.6 과 22.4 처럼 달라도 반올림하면 같아지므로, 비교는 반올림 «뒤에» 해야 한다.
import { weatherTemperatureText } from '../(tabs)/home';

describe('날씨 온도 표기', () => {
  it('최저와 최고가 다르면 둘 다 보인다', () => {
    expect(weatherTemperatureText({ minTemperature: 21, maxTemperature: 28 })).toBe('21° / 28°');
  });

  it('🔴 최저와 최고가 같으면 한 번만 보인다 — 「22° / 22°」가 되지 않는다', () => {
    expect(weatherTemperatureText({ minTemperature: 22, maxTemperature: 22 })).toBe('22°');
  });

  it('🔴 반올림해서 같아지는 값도 한 번만 보인다 (21.6 과 22.4)', () => {
    expect(weatherTemperatureText({ minTemperature: 21.6, maxTemperature: 22.4 })).toBe('22°');
  });

  it('하나만 있으면 그것만 보인다', () => {
    expect(weatherTemperatureText({ minTemperature: null, maxTemperature: 26.5 })).toBe('27°');
    expect(weatherTemperatureText({ minTemperature: 18.2, maxTemperature: null })).toBe('18°');
  });
});
