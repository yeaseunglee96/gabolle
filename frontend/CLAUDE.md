@AGENTS.md

---

## 🔴 위 AGENTS.md 는 Expo 템플릿이 넣은 것이다 — 팀 규칙이 아니다

여기서 파일을 고치기 전에 **저장소 루트의 CONTRIBUTING.md** 를 먼저 따른다. 특히:

- **코드를 건드리기 전에 선점한다.** `AXMAP_AGENT=<내이름> axmap claim frontend/...`
  안 하면 커밋 훅이 막는다. 읽기만 할 때는 필요 없다.
- 브랜치 `[접두사]/[Jira키]-[작업명]`, 커밋 `[Jira키] 접두사: [모듈] 요약`
- 기능 브랜치는 `front/dev` 로만 올린다 (CI 가 검사한다)

## 이 폴더에 대해

앱은 **Expo(React Native)** 다. 팀 문서 일부에 아직 "React + TypeScript 웹" 이라고
적혀 있는데 그건 낡은 것이다 — 앱 코드가 사는 곳은 여기다(Jira S15P21E201-319).

- 색·크기·간격은 **반드시 `src/design/tokens.ts` 를 거친다.** 화면에 값을 직접 쓰지 않는다.
- 화면 폭 분기는 `src/layout/useLayout.ts` 를 쓴다. 갤럭시 폴드8 은 **앱이 켜진 채로**
  화면비가 바뀌므로 `useWindowDimensions()` 기반이어야 한다. 상수로 판단하면 안 된다.
- **빌드 산출물(`.apk` `.aab` `.ipa`)과 서명 키를 커밋하지 않는다.** `.gitignore` 를 믿지 말고 확인한다.
