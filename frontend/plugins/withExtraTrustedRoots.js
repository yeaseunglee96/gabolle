// S15P21E201-827 — 안드로이드 앱이 OS 시스템 신뢰 저장소만 믿다가, 그 저장소가
// 갱신을 못 받는 실기기(보안 패치 2023-04-01에서 멈춘 Galaxy S10, Android 12 —
// Mainline 모듈로 오는 CA 갱신 경로가 끊긴 것으로 보임)에서 TLS 핸드셰이크가
// 조용히 실패하는 것을 확인했다(실측: 같은 기기·같은 순간 Chrome은 성공, 앱만
// "서버에 연결할 수 없어요").
//
// 🔴 서버(EC2 nginx/certbot)의 인증서 체인은 이미 ISRG Root X1까지 교차서명되도록
// 고쳐져 있음을 SSH로 직접 재확인했다 — 서버 쪽엔 더 손댈 게 없다. Chrome은 OS
// 신뢰 저장소가 아니라 2022년부터 독립된 자체 Chrome Root Store를 쓰기 때문에
// 이 기기에서도 성공한다. 앱은 OkHttp라 OS 저장소를 그대로 믿는데, 그 저장소가
// 갱신 안 된 기기에서는 ISRG Root X1/X2 자체를 모를 수 있다.
//
// 고치는 법 — OS 저장소 갱신을 기다리는 대신, 앱이 이 루트 둘을 직접 신뢰하게
// network_security_config.xml에 명시적 신뢰 앵커로 박는다. Expo 관리형
// 워크플로우라 android/ 폴더가 저장소에 없고 EAS 빌드 때마다 새로 생성되므로,
// 이 config plugin이 매 빌드마다 해당 XML과 raw 리소스를 심고 AndroidManifest에
// 연결한다.
//
// 🔴 안드로이드만 다룬다 — 이번에 재현·확인한 실패가 안드로이드 실기기에서였고,
// iOS(URLSession, 자체 문제일 수도·아닐 수도 있음)는 이 작업 범위 밖이다.

const { withAndroidManifest, withDangerousMod, AndroidConfig } = require('expo/config-plugins');
const fs = require('fs');
const path = require('path');

const NETWORK_SECURITY_CONFIG_XML = `<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config>
        <trust-anchors>
            <certificates src="@raw/isrg_root_x1" />
            <certificates src="@raw/isrg_root_x2" />
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
`;

function withExtraTrustedRoots(config) {
	config = withDangerousMod(config, [
		'android',
		async (config) => {
			const projectRoot = config.modRequest.projectRoot;
			const platformRoot = config.modRequest.platformProjectRoot;

			const rawDir = path.join(platformRoot, 'app/src/main/res/raw');
			const xmlDir = path.join(platformRoot, 'app/src/main/res/xml');
			fs.mkdirSync(rawDir, { recursive: true });
			fs.mkdirSync(xmlDir, { recursive: true });

			fs.copyFileSync(
				path.join(projectRoot, 'android-certs/isrg-root-x1.pem'),
				path.join(rawDir, 'isrg_root_x1.pem'),
			);
			fs.copyFileSync(
				path.join(projectRoot, 'android-certs/isrg-root-x2.pem'),
				path.join(rawDir, 'isrg_root_x2.pem'),
			);

			fs.writeFileSync(
				path.join(xmlDir, 'network_security_config.xml'),
				NETWORK_SECURITY_CONFIG_XML,
			);

			return config;
		},
	]);

	config = withAndroidManifest(config, (config) => {
		const mainApplication = AndroidConfig.Manifest.getMainApplicationOrThrow(config.modResults);
		mainApplication.$['android:networkSecurityConfig'] = '@xml/network_security_config';
		return config;
	});

	return config;
}

module.exports = withExtraTrustedRoots;
