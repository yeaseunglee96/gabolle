> # 🟡 앱(폰) 지도는 별개다 — 2026-09-17 에 만들었다 (`S15P21E201-1140`)
>
> **이 문서의 나머지는 전부 웹 지도 이야기다.** 폰에는 애초에 지도가 없었다 —
> `RouteMap.tsx` 가 `Platform.OS === 'web'` 일 때만 지도를 그리고, 폰에서는
> 「앱 지도 연동을 준비하고 있어요」 라는 자리표시자가 나왔다.
> 사용자 보고(2026-09-16)의 「앱 지도가 안 돼」가 이것이다. **연동이 끊긴 것이 아니라
> 만든 적이 없던 것**이라, 위의 nginx·카카오 콘솔 절차로는 아무것도 안 고쳐진다.
>
> 이제 `src/map/RouteMap.native.tsx` 가 `react-native-maps` 로 폰 지도를 그린다.
> 웹은 그대로 카카오다 — 웹 지도는 잘 돌고 있어서 안 건드렸다.
>
> | | 지도 | 키 | 지금 상태 |
> |---|---|---|---|
> | 웹 | 카카오 | `EXPO_PUBLIC_KAKAO_MAP_JS_KEY` (Jenkins) | 🟢 뜬다 |
> | **iOS** | **애플 지도** | **필요 없다** | 🟢 **빌드만 나오면 뜬다** |
> | **안드로이드** | Google Maps | `GOOGLE_MAPS_ANDROID_API_KEY` | 🔴 **키가 없다** |
>
> ## 🔴 안드로이드에 남은 사람 손 — 한 가지
>
> Google Cloud 콘솔에서 **Maps SDK for Android** 키를 발급하고, 패키지
> `com.gabolle.app` 과 Play 앱 서명 SHA-1 로 제한한 뒤, **EAS 프로젝트 환경 변수**에
> `GOOGLE_MAPS_ANDROID_API_KEY` 로 넣는다. `app.config.js` 가 그 값을 읽어 넣는다.
>
> **키가 없다고 빌드를 세우지는 않는다.** 지도 한 화면 때문에 앱 전체 배포를 막는 것은
> 과하다. 대신 안드로이드에서 지도 자리에 **회색 네모 대신 이유를 적은 안내문**이 뜬다
> (회색 네모는 사용자에게 "고장났다" 로 읽힌다).
>
> 🔴 **실기기 확인은 아직 안 됐다.** 키가 들어간 빌드가 나와야 볼 수 있다.

# 웹 지도를 되살리는 절차 — 사람이 해야 하는 부분

작성 2026-09-08 · **해결 2026-09-10** · 관련 티켓 `S15P21E201-163`(화면) · `S15P21E201-167`(키와 도메인)

> # 🟢 해결됐다 (2026-09-10 23:58 KST). 지도가 뜬다.
>
> **아래 세 후보 중 2번이 원인이었다.** 이제 추측이 아니라 실측이다.
>
> | # | 후보 | 판정 |
> |---|---|---|
> | 1 | 키가 번들에 빈 값으로 박혔다 | **아니다** — 키 32자가 이미 들어가 있었다 (MR `!517`) |
> | 2 | **서버가 카카오 스크립트를 차단한다** | 🟢 **이것이었다** |
> | 3 | 카카오 콘솔에 도메인 미등록 | **아니다** — 2번을 고치니 떴다 |
>
> ## 무엇을 바꿨나
>
> `/etc/nginx/sites-available/default` **120번 줄 하나.** 다른 항목은 안 건드렸다.
>
> ```diff
> - script-src 'self' 'unsafe-inline';
> + script-src 'self' 'unsafe-inline' https://*.kakao.com https://*.daumcdn.net;
> - connect-src 'self' https://*.google.com https://*.kakao.com https://*.naver.com;
> + connect-src 'self' https://*.google.com https://*.kakao.com https://*.naver.com https://*.daumcdn.net;
> ```
>
> `sudo nginx -t` 통과 후 `sudo systemctl reload nginx` (접속을 끊지 않는다).
> 백업: `default.bak.before-kakao-csp-20260910-145802`
>
> 🔴 **`*.daumcdn.net` 은 확인 못 한 채 미리 넣은 것이다** (4-3 절의 경고 그대로).
> 지금 지도가 뜨므로 최소한 해가 되지는 않는다. 정말 쓰이는지는 아직 모른다.
>
> ## 🔴 아래 본문은 **고치기 전** 상태를 적은 것이다
>
> 지우지 않고 남긴다 — 같은 증상이 다시 나면 이 절차가 그대로 필요하고,
> **셋 중 무엇이었는지를 아는 것**이 다음에 시간을 아낀다.
> 다만 *"지금 무엇이 막고 있나"* 는 **이제 사실이 아니다.**


이 문서는 **코드로는 못 고치는 것**만 모은 것이다. 저장소를 고쳐도 지도는 안 뜬다.
카카오 개발자 콘솔 · Jenkins · EC2 서버 **세 곳에 들어갈 수 있는 사람**이 필요하다.

> **용어**
> **번들(bundle)** — 앱 소스 수백 개를 브라우저가 읽을 수 있는 파일 한 덩어리로 합쳐 놓은 것.
> **빌드 때 박힌다** — 값이 실행 중에 읽히는 게 아니라 번들을 만드는 순간 코드 안에 문자열로
> 새겨진다는 뜻. 나중에 바꾸려면 **다시 빌드해야 한다.**
> **CSP**(Content-Security-Policy) — 서버가 브라우저에 보내는 *"이 페이지는 이런 곳에서만
> 코드·그림·글꼴을 받아라"* 는 지시. 목록에 없는 곳은 브라우저가 **조용히 차단한다.**
> **리버스 프록시** — 바깥에서 오는 요청을 먼저 받아 뒤에 있는 컨테이너로 나눠 주는 서버.
> 여기서는 EC2 위의 nginx.
> **Credential** — Jenkins 가 비밀값을 이름으로 보관해 두는 자리. 빌드 로그에 값이 안 찍힌다.

---

## 0. 지금 무엇이 막고 있나 — 잰 것만 적는다

2026-09-07 15:43 KST 에 배포 서버에 직접 요청을 보내 확인했다.

| # | 막는 것 | 어떻게 확인했나 | 누가 고칠 수 있나 |
|---|---|---|---|
| 1 | 카카오 지도 키가 **번들에 빈 값으로 박혀 있다** | `frontend/Jenkinsfile` 의 `docker build` 가 넘기는 `--build-arg` 여덟 개에 `EXPO_PUBLIC_KAKAO_MAP_JS_KEY` 가 없다 (파일을 열어 셈) | Jenkins 권한자 + 아무 개발자 |
| 2 | **서버가 카카오 스크립트를 차단한다** | `curl -sI https://j15e201.p.ssafy.io/` 응답의 `Content-Security-Policy` 에 `script-src 'self' 'unsafe-inline'` 만 있고 카카오가 없다 | **EC2 접속 권한자만** |
| 3 | 카카오 개발자 콘솔에 이 도메인이 등록돼 있는지 **모른다** | 콘솔에 못 들어갔다 — **확인 못 했다** | 카카오 계정 가진 사람 |

> 🔴 **1번만 고치면 안 뜬다.** 2번이 남아 있으면 키를 꽂아도 브라우저가 스크립트를 버린다.
> 셋을 다 해야 지도가 뜬다. 한 사람이 다 못 하므로 **세 사람을 먼저 잡는 것이 1번 일이다.**

### 코드 쪽에서 이미 한 것 (이 MR)

- 지도가 안 뜰 때 화면이 **무엇 때문인지** 말한다 — 키가 없는 것 / 서버가 막은 것 /
  도메인이 등록 안 된 것 / 파일을 못 받은 것 넷을 갈라서 띄운다. 아래 4절이 그 화면을 읽는 법이다
- 빌드할 때 이 값들이 비어 있으면 **빌드 로그에 경고가 뜬다** (`frontend/tools/check-public-env.mjs`)
- 지도와 무관하게 **3D 부산 화면으로 가는 버튼**이 지도 화면에 생겼다 — 서버 설정이 하나도 필요 없다

---

## 1. 카카오 개발자 콘솔 — JavaScript 키와 도메인

**하는 사람**: 카카오 개발자 계정을 가진 사람 (`S15P21E201-167` 담당)
**드는 시간**: 30분 안쪽

1. `https://developers.kakao.com` → 내 애플리케이션 → 해당 앱
2. **앱 키** 화면에서 **JavaScript 키**를 복사한다
   (REST API 키·네이티브 앱 키가 아니다. **JavaScript 키**여야 한다)
3. **플랫폼 → Web → 사이트 도메인**에 아래를 등록한다

   ```
   https://j15e201.p.ssafy.io
   ```

   로컬에서도 볼 사람이 있으면 `http://localhost:8081` 을 함께 등록한다
   (Expo 웹 개발 서버의 기본 포트다)
4. 저장한다

> **되돌리기**: 도메인 목록에서 그 줄을 지운다. 지우면 그 도메인에서만 지도가 안 뜬다.

> 🔴 **여기까지만 하면 아직 아무 변화가 없다.** 키가 빌드에 안 들어가 있기 때문이다 → 2절.

---

## 2. Jenkins — 키를 Credential 로 넣는다

**하는 사람**: Jenkins 권한자
**드는 시간**: 10분

1. Jenkins → `Manage Jenkins` → `Credentials` → 도메인 선택 → `Add Credentials`
2. 이렇게 채운다

   | 칸 | 값 |
   |---|---|
   | Kind | **Secret text** |
   | Secret | 1절에서 복사한 **JavaScript 키** |
   | ID | **`gabolle-kakao-map-js-key`** ← 이 이름 그대로 써야 한다. 3절이 이 이름을 부른다 |
   | Description | `카카오 지도 JavaScript 키 (웹 지도용)` |

3. 저장한다

> **되돌리기**: 그 Credential 을 지운다. 단, 3절을 이미 적용했다면 **먼저 3절을 되돌려야 한다** —
> 없는 Credential 을 `withCredentials` 가 부르면 빌드가 그 자리에서 실패한다.

---

## 3. `frontend/Jenkinsfile` — 두 줄을 더한다

**하는 사람**: 아무 개발자 (MR 로 올린다)
**드는 시간**: 10분
🔴 **2절을 먼저 끝낸 뒤에 한다.** Credential 이 없는 상태에서 이 변경을 머지하면
**프론트 배포 파이프라인이 통째로 빨개진다.**

`Build Image` 단계의 `withCredentials([...])` 목록에 한 줄, 그 아래 `docker build` 에 한 줄이다.

```diff
                     withCredentials([
                         string(credentialsId: 'gabolle-google-client-id', variable: 'GOOGLE_CLIENT_ID'),
                         string(credentialsId: 'gabolle-naver-client-id', variable: 'NAVER_CLIENT_ID'),
-                        string(credentialsId: 'gabolle-kakao-client-id', variable: 'KAKAO_CLIENT_ID')
+                        string(credentialsId: 'gabolle-kakao-client-id', variable: 'KAKAO_CLIENT_ID'),
+                        string(credentialsId: 'gabolle-kakao-map-js-key', variable: 'KAKAO_MAP_JS_KEY')
                     ]) {
```

```diff
                                 --build-arg EXPO_PUBLIC_KAKAO_CLIENT_ID="$KAKAO_CLIENT_ID" \
+                                --build-arg EXPO_PUBLIC_KAKAO_MAP_JS_KEY="$KAKAO_MAP_JS_KEY" \
```

받는 쪽(`frontend/Dockerfile` 의 `ARG` 와 `ENV`)은 **이미 되어 있다.** 건드릴 필요가 없다.

**확인**: 다음 배포의 빌드 로그에서 이 줄을 찾는다.

```
[공개 설정값] 지켜보는 값이 모두 채워져 있습니다.
```

`EXPO_PUBLIC_KAKAO_MAP_JS_KEY` 가 아직 경고 목록에 있으면 키가 안 들어간 것이다.

> 🔴 **로컬에서 시험할 때 반드시 알아야 하는 함정** (2026-09-08 실측).
> 손으로 `EXPO_PUBLIC_KAKAO_MAP_JS_KEY=<키> npx expo export --platform web` 을 돌리면
> **키가 번들에 안 들어간다.** 번들러(Metro)가 앞서 만들어 둔 결과를 재사용하는데,
> 그 판단에 이 값의 변화가 안 들어가기 때문이다. 값을 바꿔 가며 시험할 때는
> **`--clear` 를 붙인다** — 붙이기 전 0건이던 `dapi.kakao.com` 이 붙이면 1건이 된다.
> Jenkins 배포는 매번 새 컨테이너에서 굽기 때문에 이 함정에 안 걸린다.

> **되돌리기**: 위 두 줄을 지우는 MR 하나. 코드 동작은 원래대로 돌아간다
> (키가 없으면 화면이 목록 모드로 뜬다).

---

## 4. 🔴 EC2 리버스 프록시 — CSP 에 카카오를 넣는다

**하는 사람**: **EC2 에 SSH 로 들어갈 수 있는 사람뿐.** 이 설정은 저장소 어디에도 없다
**드는 시간**: 15분
🔴 **이걸 안 하면 1~3절이 전부 헛일이다.**

### 4-1. 어느 파일이 그 헤더를 붙이는지 찾는다

```bash
# 설정 파일 안에서 곧바로 찾는다
sudo grep -rn "Content-Security-Policy" /etc/nginx/

# 위에서 안 나오면, nginx 가 실제로 읽고 있는 설정 전부를 파일 이름과 함께 찍어서 본다
sudo nginx -T | grep -n -e "configuration file" -e "Content-Security-Policy"
```

> 🔴 **nginx 의 `add_header` 는 상속되다가 아래 블록에 하나라도 있으면 위엣것을 전부 버린다.**
> 그래서 **여러 군데 있으면 실제로 먹는 것은 가장 안쪽 하나**다. 4-4 의 `curl` 로
> 반드시 결과를 확인한다 — 고쳤는데 안 바뀌면 다른 블록을 고친 것이다.

### 4-2. 원본을 남긴다

```bash
FILE=<4-1 에서 찾은 파일 경로>
sudo cp "$FILE" "$FILE.bak.$(date +%Y%m%d-%H%M%S)"
ls -l "$FILE".bak.*
```

### 4-3. `script-src` 에 카카오를 더한다

지금 값(2026-09-07 실측):

```
script-src 'self' 'unsafe-inline';
```

바꿀 값 — **`script-src` 부분만** 바꾼다. 다른 항목은 그대로 둔다:

```
script-src 'self' 'unsafe-inline' https://*.kakao.com https://*.daumcdn.net;
```

같은 헤더의 `connect-src` 에도 다음 한 곳을 더한다 (지금은 `https://*.kakao.com` 만 있다):

```
connect-src 'self' https://*.google.com https://*.kakao.com https://*.naver.com https://*.daumcdn.net;
```

> 🔴 **`*.daumcdn.net` 은 확인하지 못한 채 미리 넣는 것이다.** 카카오 지도의 시작 파일
> (`dapi.kakao.com/v2/maps/sdk.js`)은 **유효한 키 없이는 401 만 주기 때문에**, 그 안에서
> 어떤 주소를 더 부르는지 이 작업에서 열어 볼 수 없었다. 카카오 지도가 다음 파일을
> 다음(daum) 쪽 주소에서 받는 구조라서 미리 넣어 둔다. **틀렸으면 지우면 된다** —
> 4-5 가 남는 차단을 화면에 그대로 띄운다.
>
> `img-src` 는 이미 `https:` 전체를 허용하므로 지도 그림 타일은 손댈 것이 없다 (실측).

### 4-4. 문법을 검사하고, 통과할 때만 적용한다

```bash
sudo nginx -t          # 여기서 실패하면 reload 하지 마라. 4-6 으로 되돌린다
sudo systemctl reload nginx
```

`reload` 는 **접속을 끊지 않고** 새 설정을 읽는다 (`restart` 와 다르다).

### 4-5. 실제로 바뀌었는지 밖에서 확인한다

```bash
curl -sI https://j15e201.p.ssafy.io/ | grep -i content-security-policy
```

출력에 `script-src ... https://*.kakao.com` 이 보이면 된 것이다.
안 보이면 **다른 블록의 `add_header` 가 이기고 있는 것**이다 → 4-1 로 돌아간다.

### 4-6. 되돌리기

```bash
sudo cp "$FILE.bak.<아까 찍힌 시각>" "$FILE"
sudo nginx -t && sudo systemctl reload nginx
curl -sI https://j15e201.p.ssafy.io/ | grep -i content-security-policy
```

---

## 5. 다 끝났는지 확인하는 법 — 화면이 스스로 말한다

배포 뒤 `https://j15e201.p.ssafy.io/demo-trip/map` 을 연다.
지도가 안 뜨면 **지도 자리에 뜨는 문구를 그대로 읽는다.** 문구마다 남은 일이 다르다.

| 화면에 뜨는 말 | 아직 안 된 것 | 어디로 |
|---|---|---|
| **지도 키가 이 빌드에 안 들어갔어요** | 키가 번들에 안 박혔다 | 2절 · 3절 |
| **이 서버가 지도 스크립트를 막고 있어요** | 서버 CSP | 4절. 작은 글씨에 **무엇이 무엇을 막았는지** 그대로 적혀 있다 |
| **지도 서버가 이 주소를 거부했어요** | 콘솔의 도메인 등록 | 1절. 작은 글씨에 **등록해야 할 도메인**이 그대로 적혀 있다 |
| **지도 파일을 못 받았어요** | 키가 틀렸거나 도메인 미등록이거나 망이 막혔다 | 1절부터 다시 |
| 아무 문구도 없고 지도가 보인다 | 🟢 끝났다 | — |

각 문구 아래 작은 회색 글씨 한 줄이 **고치는 사람이 읽을 정보**다.
개발자 도구를 안 열어도 화면에 그대로 나온다.

---

## 6. 🔴 3D 부산 화면은 위의 어느 것도 필요 없다

지도 화면의 **"3D 로 보기"** 버튼은 `https://j15e201.p.ssafy.io/city3d/` 를 새 탭
(앱에서는 앱 위에 덮이는 브라우저 탭)으로 연다.

| | |
|---|---|
| 카카오 키 | 필요 없다 |
| 서버 CSP | 손댈 것 없다 — **새 탭으로 여는 것은 CSP 가 막는 대상이 아니다** |
| 앱에 새로 깔 부품 | **0개** — `expo-web-browser` 를 소셜 로그인이 이미 쓰고 있다 |

> 나중에 3D 화면을 **새 탭이 아니라 앱 화면 안에 끼워** 보여주고 싶어지면
> (`<iframe>`), 서버 설정은 **지금 상태로도 허용된다** — `/city3d/` 응답에는
> 끼우기를 막는 헤더(`X-Frame-Options` · `frame-ancestors`)가 **하나도 안 붙어 있고**
> 앱 페이지와 **같은 출처**라 `default-src 'self'` 안에 들어온다 (2026-09-07 헤더 실측).
> 🔴 다만 **브라우저에서 실제로 끼워 보지는 못했다.**

---

## 7. 🔴 이 문서가 확인하지 못한 것

숨기지 않는다. 아래는 **모르는 채로 남아 있다.**

| 무엇 | 왜 못 했나 |
|---|---|
| 카카오 개발자 콘솔에 이 도메인이 등록돼 있는가 | 콘솔에 못 들어간다 |
| CSP 를 고치면 실제로 지도가 뜨는가 | 유효한 키가 없어 끝까지 못 가 봤다. 헤더가 스크립트를 막는 것은 CSP 규격의 동작이다 |
| 카카오 지도가 `*.daumcdn.net` 을 실제로 부르는가 | 시작 파일이 키 없이는 401 만 준다 (2026-09-08 실측) |
| EC2 nginx 설정 파일의 정확한 경로와 소유자 | 저장소에 없다. 4-1 이 찾는 방법이다 |
| 휴대폰에서 지도·3D 가 어떻게 보이는가 | **아무도 안 쟀다.** 팀의 성능 숫자는 전부 PC 한 대의 것이다 |
