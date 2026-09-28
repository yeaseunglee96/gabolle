# Play 스토어 설명 뒤에 붙이는 문단 — 권한 목적과 약관·개인정보 주소

Google Play 의 **스토어 등록정보 → 전체 설명** 맨 뒤에 붙는 문단이다 (S15P21E201-253).
언어마다 하나씩, 다섯 벌. 위의 홍보 문구는 건드리지 않고 **이 문단만 덧붙인다.**

## 왜 설명 본문에 적나

Play 에는 **약관 주소를 넣는 칸이 따로 없다.** 개인정보처리방침만 앱 콘텐츠에 칸이 있고,
이용약관은 넣을 자리가 없어서 **설명 본문에 적는 것 말고는 방법이 없다.**
권한 목적도 마찬가지다 — 앱 콘텐츠의 「앱 액세스 권한」은 *심사관이 로그인하는 방법*을
적는 칸이지 *권한을 왜 쓰는지* 적는 칸이 아니다.

## 왜 저장소에 두나

스토어 문구는 콘솔에만 있으면 **아무도 리뷰하지 않고, 누가 언제 왜 바꿨는지도 안 남는다.**
권한 목적은 특히 그렇다 — 코드에서 권한이 늘거나 줄면 **이 문단도 같이 틀려진다.**
여기 두면 MR 에 같이 올라와 함께 검토된다.

🔴 **권한을 추가·삭제하는 MR 은 이 파일도 같이 고친다.** 지금 쓰는 네 가지(위치·카메라·
사진·알림)는 사용자에게 실제로 묻는 것만 적은 것이다. 매니페스트에 있지만 **사용자에게
안 묻는 것**(예: `USE_BIOMETRIC` — `expo-secure-store` 가 기기 보안 저장소를 쓰려고
선언한다)은 여기 적지 않는다. 적으면 없는 기능을 있다고 말하는 것이 된다.

주소 둘은 실제로 열리는 것을 확인했다 (둘 다 200).

- `https://j15e201.p.ssafy.io/legal/terms`
- `https://j15e201.p.ssafy.io/legal/privacy`

---

## 매니페스트 권한 전수 — 「이 권한 왜 있나」에 답하는 표 (S15P21E201-1505)

Play 데이터 안전 양식이나 심사가 「이 권한을 왜 쓰나」를 물을 때 **여기서 답을 가져간다.**
2026-09-24 에 기기에 깔린 build 34 APK 의 `AndroidManifest.xml` 을 직접 풀어 확인한 것이다
(권한 33개). 아래 설명 문단(ko-KR 등)은 **사용자에게 실제로 묻는 것만** 적고, 이 표는 **묻지 않는
것까지 전부** 적는다.

| 권한 | 왜 있나 | 출처 | 사용자에게 묻나 |
|---|---|---|---|
| `ACCESS_COARSE_LOCATION` · `ACCESS_FINE_LOCATION` | 주변 여행지 안내, 지도에 현재 위치 표시 | 우리가 선언(`app.json`) | 묻는다 |
| `CAMERA` | 메뉴판 촬영 번역 | expo-image-picker | 묻는다 |
| `POST_NOTIFICATIONS` | 일정이 만들어졌거나 바뀐 것을 알림 | expo-notifications | 묻는다 |
| `READ_EXTERNAL_STORAGE` · `WRITE_EXTERNAL_STORAGE` | Android 12 이하에서 사진 고르기·임시 파일 | expo-image-picker · expo-file-system | 12 이하만. 둘 다 `maxSdkVersion=32` 라 **13 이상에서는 요청 목록에 안 뜬다**(APK 실측) |
| `INTERNET` · `ACCESS_NETWORK_STATE` | 서버와 통신, 연결 상태 확인 | 기본 | 안 묻는다(일반 권한) |
| `WAKE_LOCK` · `RECEIVE_BOOT_COMPLETED` | 알림 수신, 재부팅 뒤 예약 알림 되살리기 | expo-notifications · Firebase | 안 묻는다 |
| `VIBRATE` | 알림 진동 | expo-notifications | 안 묻는다 |
| `MODIFY_AUDIO_SETTINGS` | 오디오·영상 재생 | expo-audio | 안 묻는다 |
| `USE_BIOMETRIC` · `USE_FINGERPRINT` | 로그인 토큰을 기기 보안 저장소에 넣는 라이브러리가 선언. 앱이 생체 인증 화면을 띄우는 게 아니다 | expo-secure-store(androidx.biometric) | 안 묻는다 |
| `com.google.android.c2dm.permission.RECEIVE` | 푸시(FCM) 수신 | Firebase | 안 묻는다 |
| `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE` | Play 설치 출처 조회 | Google 라이브러리(정확한 출처 미확정) | 안 묻는다 |
| 런처 배지 21개 (`com.sec.…badge.permission.READ` 등 삼성·HTC·소니·화웨이·오포 계열, `READ_APP_BADGE`) | 런처별 아이콘 숫자 배지를 그리는 ShortcutBadger 가 선언. 앱 코드는 배지 숫자를 직접 쓰지 않는다(`setBadgeCount` 호출 없음) | expo-notifications 의존 | 안 묻는다(일반 권한) |
| `com.gabolle.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX 내부용. 우리 앱만 쓰는 서명 권한 | AndroidX | 해당 없음 |

**일부러 뺀 것 (`tools:node="remove"` / `blockedPermissions`)**

- `FOREGROUND_SERVICE` · `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — 배경 재생을 안 쓴다
  (`plugins/withoutMediaForegroundService.js`, S15P21E201-1504)
- `SYSTEM_ALERT_WINDOW`(다른 앱 위에 그리기) — 여행 앱에 필요 없다. `app.json` 의
  `android.blockedPermissions` 로 뺐다.

🔴 **`SYSTEM_ALERT_WINDOW` 의 출처는 티켓 초안이 짐작한 «react-native 의 debug 매니페스트» 가 아니다.**
Expo 템플릿(`expo-template-bare-minimum`)의 **main 매니페스트**가 직접 선언한다 — 템플릿 안에
「OPTIONAL PERMISSIONS, REMOVE WHATEVER YOU DO NOT NEED」라는 주석과 함께 있다. 그래서 릴리스
빌드에 들어왔고, Expo 가 이런 권한을 빼라고 준 정식 수단이 `blockedPermissions` 다. 같은 주석 아래
있는 `VIBRATE` 는 알림 진동에 쓰이므로 남긴다.

---

## ko-KR

```text
권한 사용 목적

모든 권한은 선택입니다. 허용하지 않아도 일정을 만들고 보는 기능은 그대로 쓸 수 있습니다.

- 위치: 지금 있는 곳 주변의 여행지를 안내하고, 지도에 현재 위치를 표시할 때 씁니다.
- 카메라: 메뉴판을 찍어 번역할 때만 씁니다. 찍은 사진은 저장하지 않습니다.
- 사진: 여행 기록에 올릴 사진을 고를 때만 씁니다. 고른 사진만 올라갑니다.
- 알림: 일정이 만들어졌거나 바뀐 것을 알려드릴 때 씁니다.

이용약관 https://j15e201.p.ssafy.io/legal/terms
개인정보 처리방침 https://j15e201.p.ssafy.io/legal/privacy
```

## en-US

```text
PERMISSIONS AND WHY

Every permission is optional. You can build and open itineraries without granting any of them.

• Location — to suggest places near where you are and to show your position on the map.
• Camera — only to photograph a menu for translation. The photo is not stored.
• Photos — only to pick a photo for a travel record. Only the photo you pick is uploaded.
• Notifications — to tell you when an itinerary has been built or changed.

Terms of Service: https://j15e201.p.ssafy.io/legal/terms
Privacy Policy: https://j15e201.p.ssafy.io/legal/privacy
```

## ja-JP

```text
権限と、その用途

権限はすべて任意です。許可しなくても、旅程をつくる・見る機能はそのまま使えます。

• 位置情報 — 今いる場所の周辺の観光地をご案内し、地図に現在地を表示するために使います。
• カメラ — メニューを撮って翻訳するときだけ使います。撮った写真は保存しません。
• 写真 — 旅の記録に載せる写真を選ぶときだけ使います。選んだ写真だけがアップロードされます。
• 通知 — 旅程がつくられたとき、変わったときにお知らせするために使います。

利用規約 https://j15e201.p.ssafy.io/legal/terms
プライバシーポリシー https://j15e201.p.ssafy.io/legal/privacy
```

## zh-CN

```text
权限用途

所有权限都是可选的。不授予也可以正常创建和查看行程。

• 位置 — 用于推荐你所在位置附近的景点,以及在地图上显示你的位置。
• 相机 — 仅在拍摄菜单进行翻译时使用,拍摄的照片不会保存。
• 照片 — 仅在选择要上传到旅行记录的照片时使用,只有你选中的照片会上传。
• 通知 — 在行程生成或发生变化时通知你。

服务条款 https://j15e201.p.ssafy.io/legal/terms
隐私政策 https://j15e201.p.ssafy.io/legal/privacy
```

## zh-TW

```text
權限用途

所有權限都是選用的。不授權也可以正常建立和查看行程。

• 位置 — 用於推薦你所在位置附近的景點,以及在地圖上顯示你的位置。
• 相機 — 僅在拍攝菜單進行翻譯時使用,拍攝的照片不會保存。
• 照片 — 僅在選擇要上傳到旅行記錄的照片時使用,只有你選取的照片會上傳。
• 通知 — 在行程產生或變更時通知你。

服務條款 https://j15e201.p.ssafy.io/legal/terms
隱私權政策 https://j15e201.p.ssafy.io/legal/privacy
```
