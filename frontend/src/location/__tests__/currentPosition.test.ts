jest.mock('expo-location', () => ({
  Accuracy: { Balanced: 3 },
  getLastKnownPositionAsync: jest.fn(),
  getCurrentPositionAsync: jest.fn(),
}));
import * as Location from 'expo-location';
import { PositionTimeoutError, readCurrentPosition } from '@/location/currentPosition';

declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync } = require('fs');

const last = Location.getLastKnownPositionAsync as jest.Mock;
const current = Location.getCurrentPositionAsync as jest.Mock;
const fix = (timestamp: number) => ({ timestamp, coords: { latitude: 35.1, longitude: 129.0 } });

afterEach(() => { jest.useRealTimers(); jest.resetAllMocks(); });

test('최근에 알던 위치가 있으면 새로 묻지 않는다', async () => {
  last.mockResolvedValue(fix(Date.now()));
  await expect(readCurrentPosition()).resolves.toMatchObject({ coords: { latitude: 35.1 } });
  expect(current).not.toHaveBeenCalled();
});

test('GPS 가 답을 안 하면 8초 뒤에 포기한다 — 끝없이 기다리지 않는다', async () => {
  jest.useFakeTimers();
  last.mockResolvedValue(null);
  current.mockReturnValue(new Promise(() => undefined));
  const pending = readCurrentPosition();
  const assertion = expect(pending).rejects.toBeInstanceOf(PositionTimeoutError);
  await jest.advanceTimersByTimeAsync(8000);
  await assertion;
});

test('오래된 위치는 버리고 새로 묻는다', async () => {
  last.mockResolvedValue(fix(Date.now() - 10 * 60 * 1000));
  current.mockResolvedValue(fix(Date.now()));
  await readCurrentPosition();
  expect(current).toHaveBeenCalled();
});

test.each([['app/(tabs)/feed.tsx'], ['app/explore.tsx'], ['app/field/transit.tsx']])('%s 는 시간 제한 없는 getCurrentPositionAsync 를 직접 부르지 않는다', (file) => {
  const source: string = readFileSync(`${__dirname}/../../../${file}`, 'utf8');
  expect(source).not.toMatch(/getCurrentPositionAsync/);
  expect(source).toMatch(/readCurrentPosition\(/);
});
