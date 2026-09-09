# Frontend

🔴 **이 폴더는 자리표시자다. 실제 프론트엔드 코드는 여기 없다.**

`common/dev`는 `front/main → main`으로 이어지는 파트 브랜치 사다리 밖에 있다
(`docs/git-convention.md`의 브랜치 그림 참고 — `feature/front/* → front/dev → front/main → main`).
그래서 `common/dev`의 이 폴더에는 실제 Expo 프로젝트가 한 번도 들어온 적이 없고,
앞으로도 이 경로로는 들어오지 않는다.

실제 프론트엔드(Expo/React Native)는 `front/dev` 브랜치에 있다.

```bash
git fetch origin front/dev
git show origin/front/dev:frontend/README.md   # 실제 프론트엔드 README
```

> 🔴 **정정 이력 (2026-09-03).** 이 README는 2026-08-25 프로젝트 초기 구조 정리
> (S15P21E201-6) 때 "서비스 명세 확정 후 추가합니다" 자리표시자로 만들어진 뒤
> 한 번도 갱신되지 않았다. 그 사이 `front/dev`에 실제 프로젝트(Expo 기반, 이
> 자리표시자가 말하는 "React + TypeScript 웹"이 아니다)가 만들어졌는데, 이
> 자리표시자는 갱신되지 않은 채 남아 있었다.
> MR !105에서 이 폴더를 실제 코드가 있는 곳인 줄 알고 CI 빌드 검사를 만들었다가
> 대상이 빈 폴더라는 걸 파이프라인 실패로 뒤늦게 알았다 — 그 검사는
> `front/dev`에 다시 만들어 머지했다 (S15P21E201-269, MR !107).
