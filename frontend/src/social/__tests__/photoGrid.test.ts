import { PHOTO_GRID_MAX, photoGridSlots, planPhotoGrid } from '../photoGrid';

const 세로 = { width: 1000, height: 1500 };
const 가로 = { width: 1600, height: 900 };
const 모름 = {};

describe('사진 배치 정하기', () => {
  it('사진이 없으면 아무 배치도 없다', () => {
    expect(planPhotoGrid([])).toBeNull();
  });

  it('한 장은 크게, 두 장은 좌우 반반', () => {
    expect(planPhotoGrid([세로])).toBe('single');
    expect(planPhotoGrid([세로, 가로])).toBe('pair');
  });

  it('🔴 세 장은 첫 장의 방향에 따라 갈린다', () => {
    // 넘겨받은 예시 — 첫 장이 세로, 뒤의 둘이 가로
    expect(planPhotoGrid([세로, 가로, 가로])).toBe('leftBig');
    // 첫 장이 가로면 위를 넓게 쓰는 편이 자연스럽다
    expect(planPhotoGrid([가로, 세로, 세로])).toBe('topWide');
  });

  it('크기를 못 재면 기본 배치로 간다 — 단정하지 않는다', () => {
    expect(planPhotoGrid([모름, 모름, 모름])).toBe('leftBig');
    // 0 이나 음수는 «잰 값»이 아니다
    expect(planPhotoGrid([{ width: 0, height: 0 }, 가로, 가로])).toBe('leftBig');
  });

  it('네 장은 2×2', () => {
    expect(planPhotoGrid([세로, 가로, 세로, 가로])).toBe('quad');
  });

  it('상한을 넘겨도 배치가 깨지지 않는다 — 뒤는 안 그린다', () => {
    const 여섯장 = [세로, 가로, 세로, 가로, 세로, 가로];
    expect(planPhotoGrid(여섯장)).toBe('quad');
    expect(photoGridSlots('quad')).toBe(PHOTO_GRID_MAX);
  });

  it('배치마다 그릴 칸 수가 정해져 있다', () => {
    expect(photoGridSlots('single')).toBe(1);
    expect(photoGridSlots('pair')).toBe(2);
    expect(photoGridSlots('leftBig')).toBe(3);
    expect(photoGridSlots('topWide')).toBe(3);
    expect(photoGridSlots('quad')).toBe(4);
  });
});
