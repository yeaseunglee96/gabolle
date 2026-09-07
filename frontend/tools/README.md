# 기기별 화면 확인

대표 기기 10종의 폭에서 화면이 어떻게 보이는지 **한 화면에 놓고** 본다.

```bash
npx expo start --web          # 앱 (8081)
node tools/serve.mjs          # 확인판 (8090)
```
브라우저로 `http://localhost:8090` 를 연다. 다른 화면을 보려면 `#/경로` 를 붙인다 —
예: `http://localhost:8090/#/constraints`

## 왜 iframe 인가

브라우저 창을 줄이는 방법으로는 확인이 안 된다. 실제로 시도했는데 `resize_window` 가
성공을 반환하고도 `window.innerWidth` 가 안 바뀌었다(창이 최대화 상태거나 OS 배율 탓).

**iframe 안에서는 `window.innerWidth` 가 iframe 의 폭이 된다.** 그래서 `useWindowDimensions`
가 진짜 기기처럼 반응하고, 열 개를 동시에 볼 수 있다.

## 왜 이게 필요한가 — 실제로 여기서 버그를 잡았다

`Screen` 이 태블릿 최대폭을 480 으로 묶고 있었는데 그 안에 2단(`Split`)이 들어가면서,
master 320dp 를 빼고 나면 detail 에 **112dp** 만 남았다. 글자가 한 글자씩 줄바꿈됐다 —
`최 단 경 로`.

🔴 **`npx tsc --noEmit` 도 `npx expo export` 도 종료 코드 0 이었다.**
폭이 좁은 것은 문법 오류가 아니다. 브라우저로 띄워 보고서야 찾았다.

## 기기 목록을 고칠 때

`devices.html` 의 `DEVICES` 배열을 고친다. 숫자는 **CSS 픽셀(논리 해상도)** 이지 물리
해상도가 아니다. 폴드8 펼침 폭이 `useLayout` 의 태블릿 기준(최단변 600dp)을 넘는지가
이 표의 핵심이다.
