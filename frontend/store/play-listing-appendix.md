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
