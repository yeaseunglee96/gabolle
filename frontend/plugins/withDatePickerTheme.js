// S15P21E201-1132 — 안드로이드 날짜 선택기가 앱과 다른 청록색으로 뜨는 것을 고친다.
//
// 증상(2026-09-16 Galaxy S10 · versionCode 7 실기기): 여행 만들기 1단계에서 「달력」을
// 누르면 뜨는 선택기의 머리글 배경·선택된 날짜 동그라미·「취소/확인」 글자가 전부 청록이다.
// 가볼래는 주황(브랜드)과 남색(동작)만 쓰는데 이 화면만 색이 다르다.
//
// 왜 청록인가 — 앱이 고른 색이 아니라 **안드로이드 기본값**이다. 경로를 따라가면 이렇다.
//
//   1. 이 앱이 쓰는 선택기는 android.app.DatePickerDialog 다
//      (@react-native-community/datetimepicker 의 RNDismissableDatePickerDialog 가
//       그것을 상속한다. display 를 안 주면 이 경로로 간다)
//   2. DatePickerDialog 는 테마를 안 받으면 화면 테마에서 android:datePickerDialogTheme
//      를 찾는다. 우리 AppTheme 에는 그 값이 없다
//   3. 그러면 AlertDialog 의 기본 테마(android:Theme.Material.Light.Dialog.Alert)로 떨어지고,
//      그 테마의 colorAccent 가 안드로이드 기본 청록(#009688)이다
//
// 고치는 자리는 그래서 2번 — **화면 테마에 datePickerDialogTheme 을 박아 준다.** 앱 코드나
// JS prop 으로는 못 바꾼다(그 prop 들은 iOS 전용이거나 버튼 글자색만 바꾼다).
//
// 🔴 남색(#0b1d3a)을 쓰고 주황(#f26532)을 안 쓴다 — 취향이 아니라 대비 때문이다.
//    colorAccent 하나가 머리글 배경·동그라미·버튼 글자 **셋 다**를 정한다. 주황으로 하면
//    흰 바탕 위의 「취소/확인」 글자가 주황이 되는데 대비가 3.2:1 이라 본문 크기 글자의
//    기준(4.5:1)에 못 미친다. 남색은 흰 바탕에서 14.8:1, 남색 바탕의 흰 글자도 같은 값이라
//    세 자리 모두 안전하다. 날짜를 고르는 것은 동작이고, 앱의 주 CTA 색도 남색이다.
//
// 🔴 AppTheme 전체의 colorAccent 를 바꾸지 않는다. 그러면 알림창·글자 커서·체크박스까지
//    한꺼번에 딸려 온다 — 이번 티켓이 재현·확인한 범위는 날짜 선택기 하나뿐이고,
//    확인 못 한 화면을 같이 바꾸는 것은 고치는 것이 아니라 거는 것이다.

const { withAndroidStyles } = require('expo/config-plugins');

/** 동작 남색. frontend/src/design/tokens.ts 의 color.action.primary 와 같은 값이다. */
const ACTION_NAVY = '#0b1d3a';
const DIALOG_THEME = 'GabolleDatePickerDialog';

function upsertItem(style, name, value) {
	style.item = style.item ?? [];
	const found = style.item.find((item) => item.$ && item.$.name === name);
	if (found) {
		found._ = value;
		return;
	}
	style.item.push({ _: value, $: { name } });
}

function withDatePickerTheme(config) {
	return withAndroidStyles(config, (config) => {
		const styles = config.modResults.resources.style ?? [];

		// prebuild 는 여러 번 돌 수 있다. 같은 style 을 두 번 밀어 넣지 않는다.
		let dialog = styles.find((style) => style.$ && style.$.name === DIALOG_THEME);
		if (!dialog) {
			dialog = { $: { name: DIALOG_THEME, parent: 'android:Theme.Material.Light.Dialog.Alert' }, item: [] };
			styles.push(dialog);
		}
		upsertItem(dialog, 'android:colorAccent', ACTION_NAVY);

		const appTheme = styles.find((style) => style.$ && style.$.name === 'AppTheme');
		// 못 찾으면 조용히 넘어가지 않는다 — 조용히 넘어가면 빌드는 되고 색만 그대로라,
		// 기기에서 보기 전까지 아무도 실패를 모른다.
		if (!appTheme) {
			throw new Error(
				'withDatePickerTheme: android/app/src/main/res/values/styles.xml 에 AppTheme 이 없다. ' +
					'Expo 템플릿이 테마 이름을 바꿨을 수 있으니 이 플러그인의 이름을 맞춰야 한다.',
			);
		}
		upsertItem(appTheme, 'android:datePickerDialogTheme', `@style/${DIALOG_THEME}`);

		config.modResults.resources.style = styles;
		return config;
	});
}

module.exports = withDatePickerTheme;
