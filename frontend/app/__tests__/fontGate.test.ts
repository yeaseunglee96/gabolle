// 글꼴이 실패해도 앱이 뜬다 — S15P21E201-1510.
import { shouldWaitForFonts } from '@/design/fontGate';

describe('글꼴 기다리기', () => {
	it('아직 로딩 중이면 기다린다', () => {
		expect(shouldWaitForFonts(false, null)).toBe(true);
	});

	it('다 실렸으면 안 기다린다', () => {
		expect(shouldWaitForFonts(true, null)).toBe(false);
	});

	// 🔴 이 한 줄이 이 파일의 이유다. 예전에는 오류를 안 받아서 실패가 「아직 로딩 중」과
	//    구분되지 않았고, 글꼴이 한 번 실패하면 앱이 «영원히» 빈 화면이었다. 화면은 한 판
	//    색에 접근성 요소 0개, 프로세스는 살아 있음 — 밖에서는 그냥 검은 화면으로 보인다.
	//    게다가 그 분기가 AppErrorBoundary 바깥이라 오류 화면조차 안 떴다.
	it('🔴 실패했으면 «기다리지 않는다» — 기기 기본 글꼴로라도 앱을 띄운다', () => {
		expect(shouldWaitForFonts(false, new Error('font not found'))).toBe(false);
	});

	// expo-font 는 실패 뒤에도 loaded 를 true 로 올리지 않는다. 둘 다 온 상태를 굳이
	// 보는 것은, 앞으로 어느 쪽이 먼저 와도 앱이 갇히지 않는다는 뜻을 남기기 위해서다.
	it('실렸는데 오류도 있으면 그래도 안 기다린다', () => {
		expect(shouldWaitForFonts(true, new Error('일부 실패'))).toBe(false);
	});
});
