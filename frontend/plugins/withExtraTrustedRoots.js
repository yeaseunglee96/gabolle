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
// network_security_config.xml에 명시적 신뢰 앵커로 박는다.
//
// 🔴 2026-09-11 정정 — 처음엔 인증서를 별도 파일(frontend/android-certs/*.pem)로
//    두고 플러그인이 빌드 시점에 복사하는 방식으로 했었다. 로컬 prebuild는 성공했지만
//    EAS 클라우드 빌드에서 매번 "ENOENT: no such file or directory, copyfile
//    .../android-certs/isrg-root-x1.pem"로 실패했다 — .gitignore의 *.pem 예외
//    규칙을 추가하고(!/android-certs/*.pem), git으로 커밋·병합까지 확인했는데도
//    재현됐다. EAS Build의 프로젝트 업로드 단계가 정확히 무엇을 기준으로 파일을
//    빼는지 끝내 못 밝혔다 — git 추적 여부도, .gitignore 재확인도 전부 정상이었는데
//    클라우드에서만 그 파일이 없었다. 원인 규명 대신 그 파일 자체를 없앴다 — 인증서
//    내용을 이 플러그인 파일 안에 문자열로 직접 박으면, 이 .js 파일 자체가 안 올라가지
//    않는 한(그러면 애초에 다른 이유로 빌드가 안 된다) 항상 존재가 보장된다.
//
// 🔴 안드로이드만 다룬다 — 이번에 재현·확인한 실패가 안드로이드 실기기에서였고,
// iOS(URLSession, 자체 문제일 수도·아닐 수도 있음)는 이 작업 범위 밖이다.

const { withAndroidManifest, withDangerousMod, AndroidConfig } = require('expo/config-plugins');
const fs = require('fs');
const path = require('path');

// Let's Encrypt 공식 배포 페이지(letsencrypt.org/certs/isrgrootx1.pem, isrg-root-x2.pem)에서
// 받은 공개 루트 인증서 원문 그대로다 — 개인키가 아니다.
const ISRG_ROOT_X1_PEM = `-----BEGIN CERTIFICATE-----
MIIFazCCA1OgAwIBAgIRAIIQz7DSQONZRGPgu2OCiwAwDQYJKoZIhvcNAQELBQAw
TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh
cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMTUwNjA0MTEwNDM4
WhcNMzUwNjA0MTEwNDM4WjBPMQswCQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJu
ZXQgU2VjdXJpdHkgUmVzZWFyY2ggR3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBY
MTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAK3oJHP0FDfzm54rVygc
h77ct984kIxuPOZXoHj3dcKi/vVqbvYATyjb3miGbESTtrFj/RQSa78f0uoxmyF+
0TM8ukj13Xnfs7j/EvEhmkvBioZxaUpmZmyPfjxwv60pIgbz5MDmgK7iS4+3mX6U
A5/TR5d8mUgjU+g4rk8Kb4Mu0UlXjIB0ttov0DiNewNwIRt18jA8+o+u3dpjq+sW
T8KOEUt+zwvo/7V3LvSye0rgTBIlDHCNAymg4VMk7BPZ7hm/ELNKjD+Jo2FR3qyH
B5T0Y3HsLuJvW5iB4YlcNHlsdu87kGJ55tukmi8mxdAQ4Q7e2RCOFvu396j3x+UC
B5iPNgiV5+I3lg02dZ77DnKxHZu8A/lJBdiB3QW0KtZB6awBdpUKD9jf1b0SHzUv
KBds0pjBqAlkd25HN7rOrFleaJ1/ctaJxQZBKT5ZPt0m9STJEadao0xAH0ahmbWn
OlFuhjuefXKnEgV4We0+UXgVCwOPjdAvBbI+e0ocS3MFEvzG6uBQE3xDk3SzynTn
jh8BCNAw1FtxNrQHusEwMFxIt4I7mKZ9YIqioymCzLq9gwQbooMDQaHWBfEbwrbw
qHyGO0aoSCqI3Haadr8faqU9GY/rOPNk3sgrDQoo//fb4hVC1CLQJ13hef4Y53CI
rU7m2Ys6xt0nUW7/vGT1M0NPAgMBAAGjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNV
HRMBAf8EBTADAQH/MB0GA1UdDgQWBBR5tFnme7bl5AFzgAiIyBpY9umbbjANBgkq
hkiG9w0BAQsFAAOCAgEAVR9YqbyyqFDQDLHYGmkgJykIrGF1XIpu+ILlaS/V9lZL
ubhzEFnTIZd+50xx+7LSYK05qAvqFyFWhfFQDlnrzuBZ6brJFe+GnY+EgPbk6ZGQ
3BebYhtF8GaV0nxvwuo77x/Py9auJ/GpsMiu/X1+mvoiBOv/2X/qkSsisRcOj/KK
NFtY2PwByVS5uCbMiogziUwthDyC3+6WVwW6LLv3xLfHTjuCvjHIInNzktHCgKQ5
ORAzI4JMPJ+GslWYHb4phowim57iaztXOoJwTdwJx4nLCgdNbOhdjsnvzqvHu7Ur
TkXWStAmzOVyyghqpZXjFaH3pO3JLF+l+/+sKAIuvtd7u+Nxe5AW0wdeRlN8NwdC
jNPElpzVmbUq4JUagEiuTDkHzsxHpFKVK7q4+63SM1N95R1NbdWhscdCb+ZAJzVc
oyi3B43njTOQ5yOf+1CceWxG1bQVs5ZufpsMljq4Ui0/1lvh+wjChP4kqKOJ2qxq
4RgqsahDYVvTH9w7jXbyLeiNdd8XM2w9U/t7y0Ff/9yi0GE44Za4rF2LN9d11TPA
mRGunUHBcnWEvgJBQl9nJEiU0Zsnvgc/ubhPgXRR4Xq37Z0j4r7g1SgEEzwxA57d
emyPxgcYxn/eR44/KJ4EBs+lVDR3veyJm+kXQ99b21/+jh5Xos1AnX5iItreGCc=
-----END CERTIFICATE-----
`;

const ISRG_ROOT_X2_PEM = `-----BEGIN CERTIFICATE-----
MIICGzCCAaGgAwIBAgIQQdKd0XLq7qeAwSxs6S+HUjAKBggqhkjOPQQDAzBPMQsw
CQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJuZXQgU2VjdXJpdHkgUmVzZWFyY2gg
R3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBYMjAeFw0yMDA5MDQwMDAwMDBaFw00
MDA5MTcxNjAwMDBaME8xCzAJBgNVBAYTAlVTMSkwJwYDVQQKEyBJbnRlcm5ldCBT
ZWN1cml0eSBSZXNlYXJjaCBHcm91cDEVMBMGA1UEAxMMSVNSRyBSb290IFgyMHYw
EAYHKoZIzj0CAQYFK4EEACIDYgAEzZvVn4CDCuwJSvMWSj5cz3es3mcFDR0HttwW
+1qLFNvicWDEukWVEYmO6gbf9yoWHKS5xcUy4APgHoIYOIvXRdgKam7mAHf7AlF9
ItgKbppbd9/w+kHsOdx1ymgHDB/qo0IwQDAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0T
AQH/BAUwAwEB/zAdBgNVHQ4EFgQUfEKWrt5LSDv6kviejM9ti6lyN5UwCgYIKoZI
zj0EAwMDaAAwZQIwe3lORlCEwkSHRhtFcP9Ymd70/aTSVaYgLXTWNLxBo1BfASdW
tL4ndQavEi51mI38AjEAi/V3bNTIZargCyzuFJ0nN6T5U6VR5CmD1/iQMVtCnwr1
/q4AaOeMSQ+2b1tbFfLn
-----END CERTIFICATE-----
`;

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
			const platformRoot = config.modRequest.platformProjectRoot;

			const rawDir = path.join(platformRoot, 'app/src/main/res/raw');
			const xmlDir = path.join(platformRoot, 'app/src/main/res/xml');
			fs.mkdirSync(rawDir, { recursive: true });
			fs.mkdirSync(xmlDir, { recursive: true });

			fs.writeFileSync(path.join(rawDir, 'isrg_root_x1.pem'), ISRG_ROOT_X1_PEM);
			fs.writeFileSync(path.join(rawDir, 'isrg_root_x2.pem'), ISRG_ROOT_X2_PEM);
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
