import { resolvePlaceVisual } from '../PlaceVisual';

describe('resolvePlaceVisual', () => {
  it.each([
    ['해운대 해수욕장', null, 'haeundae'],
    ['동백섬', '부산광역시 해운대구', 'haeundae'],
    ['감천문화마을', '부산 사하구', 'gamcheon'],
    ['광안리 해수욕장', null, 'gwangalli'],
    ['Gwangan Bridge', null, 'gwangalli'],
  ])('%s에 실제로 대응하는 이미지만 고른다', (name, address, expected) => {
    expect(resolvePlaceVisual(name, address)).toBe(expected);
  });

  it('대응 이미지가 없는 장소에는 임의 사진을 붙이지 않는다', () => {
    expect(resolvePlaceVisual('범어사', '부산 금정구')).toBeNull();
  });
});
