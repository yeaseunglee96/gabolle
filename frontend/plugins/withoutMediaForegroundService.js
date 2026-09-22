// S15P21E201-1504 — Play Console 이 「앱 업데이트를 계속 출시하려면」이라며 새 빌드를
// 막고 있었다. 이유는 포그라운드 서비스 권한 선언이고, 규정 준수 기한(2024-01-31)이
// 이미 지난 상태였다.
//
// 🔴 그 권한을 «우리가 선언한 적이 없다.» app.json 의 android.permissions 에는 위치 둘뿐이다.
//    딸려 들어온 경로는 이렇다.
//
//      expo-video  →  androidx.media3:media3-session  (expo-video/android/build.gradle)
//                     └ 이 AAR 의 매니페스트가 FOREGROUND_SERVICE 와
//                       FOREGROUND_SERVICE_MEDIA_PLAYBACK 을 스스로 선언한다
//                       → 매니페스트 병합으로 우리 앱에 들어온다
//
//    expo-video 플러그인의 supportsBackgroundPlayback 옵션은 «서비스 등록» 만 제어한다
//    (withExpoVideo.js 의 if (supportsBackgroundPlayback) 블록). 우리는 그 옵션을 안 켰는데도
//    라이브러리가 자기 매니페스트에 적은 권한은 그대로 병합된다 — 옵션으로 못 막는다.
//
// ■ 왜 「선언」이 아니라 「제거」인가
//
// 우리가 쓰는 영상은 ScenicVideo.tsx 하나다 — 로그인 화면 배경의 음소거·반복 장식 영상이고
// allowsPictureInPicture={false} 다. 배경 재생을 쓰지 않는다.
//
// Google 이 준 길은 둘이었다: (가) 안 쓰면 지워라 (나) 쓰면 선언서를 써라.
// (나) 를 고르면 «안 쓰는 기능을 쓴다» 고 적는 것이고, Google 은 그 기능의 시연을 요구한다.
// 보여 줄 것이 없다. 안 쓰는 권한은 지우는 것이 맞다.
//
// ■ tools:node="remove" 가 하는 일
//
// 매니페스트 병합기에게 «이 권한은 최종 결과에서 빼라» 고 지시한다. 라이브러리 쪽 매니페스트를
// 고치는 것이 아니라 병합 결과에서 걷어내는 것이라, 라이브러리를 판올림해도 계속 듣는다.
//
// 🔴 확인은 실기기에서 한다. 이 권한을 지워서 media3 의 재생 경로가 깨지는지는 매니페스트를
//    보는 것으로 알 수 없다 — 로그인 화면의 배경 영상이 «여전히 도는지» 를 눈으로 봐야 한다.
//    그것이 이 변경의 유일한 위험이다.

const { withAndroidManifest } = require('expo/config-plugins');

const TOOLS_NAMESPACE = 'http://schemas.android.com/tools';

/** 걷어낼 권한. 둘 다 media3-session 이 넣는다. */
const REMOVED_PERMISSIONS = [
	'android.permission.FOREGROUND_SERVICE',
	'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
];

module.exports = function withoutMediaForegroundService(config) {
	return withAndroidManifest(config, (config) => {
		const manifest = config.modResults.manifest;

		// tools: 이름공간이 없으면 tools:node 가 그냥 모르는 속성으로 무시된다.
		manifest.$ = manifest.$ || {};
		if (!manifest.$['xmlns:tools']) {
			manifest.$['xmlns:tools'] = TOOLS_NAMESPACE;
		}

		manifest['uses-permission'] = manifest['uses-permission'] || [];

		for (const name of REMOVED_PERMISSIONS) {
			const existing = manifest['uses-permission'].find(
				(entry) => entry.$ && entry.$['android:name'] === name,
			);
			if (existing) {
				existing.$['tools:node'] = 'remove';
			}
			else {
				// 우리 매니페스트에는 없고 병합으로 들어오는 것이라, 「지우라」는 표시만 남긴다.
				manifest['uses-permission'].push({ $: { 'android:name': name, 'tools:node': 'remove' } });
			}
		}

		return config;
	});
};
