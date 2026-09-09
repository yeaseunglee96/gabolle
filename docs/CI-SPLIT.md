# 파트 CI 잡을 `ci/parts/<파트>.yml` 로 옮기기

**2026-09-09 에 적었다.** 여섯 파트를 `main` 으로 올리던 날, 파트마다 걸린 충돌
파일 아홉~열 개 중 아홉은 「main 쪽 받기」로 기계적으로 끝났는데, 손으로 풀어야
한 것은 뿌리 `.gitlab-ci.yml` **하나뿐**이었다. 원인은 파트마다 자기 잡을 그
공용 파일 하나에 넣고 있었다는 것 — 파일이 하나면 파트가 늘어날수록 충돌도
늘어난다. 그리고 위험했다: bigData 의 LFS(**Large File Storage** — git 이 큰
파일을 저장소 밖에 두고 포인터만 커밋하는 방식) 훅 우회 한 줄
(`git config core.hooksPath /dev/null`, S15P21E201-741)을 main 쪽으로 잘못
받으면 태그 push 가 다시 죽는다. 그래서 파트 잡을 파트별 파일로 쪼갠다 —
뿌리 파일에는 공용 잡만 남기고, 각 파트는 자기 파일만 고친다.

이 문서는 **자기 파트의 잡을 옮기는 사람**이 보는 절차다.

---

## 0. 옮기기 전에

```bash
npx -y axmap-cli@latest claim "ci/parts/<파트>.yml" "ci" --task <Jira키> --intent "<파트> CI 잡을 분리 파일로 옮긴다"
```

뿌리 `.gitlab-ci.yml` 은 **건드리지 않는다.** `include` 목록은 여섯 파트가 한
번에 다 적혀 있고 얼려 둔 것이다 — 거기를 고치면 이 문서가 막으려던 충돌이
그대로 재발한다.

## 1. 잡을 잘라 옮긴다

1. 뿌리 `.gitlab-ci.yml` 에서 **자기 파트가 만든** 잡 블록(예: `backend:build` ·
   `frontend:smoke`)을 찾는다. 공용 잡(`claims` · `mr:gates` ·
   `verify:mr-target` · `governance` · `version` · `jira` · `promote` ·
   `vote:recheck` · `status:publish`)은 건드리지 않는다.
2. 그 블록을 통째로 잘라 `ci/parts/<파트>.yml` 에 붙여 넣는다. 파일 머리의
   안내 주석은 지우지 말고 그 아래에 잡을 둔다.
3. 뿌리 `.gitlab-ci.yml` 에서 그 블록을 지운다.

## 2. 🔴 옮기면 안 되는 것

| 키 | 왜 |
|---|---|
| `default` | 모든 잡의 기본값(이미지·`before_script`)이다. 파트 파일에 다시 적으면 그 파트 잡만 다른 기본값을 쓰게 되어 조용히 어긋난다 |
| `stages` | 파트 파일에서 다시 선언하면 **뿌리의 선언을 덮어쓴다.** GitLab 은 `stages` 를 하나만 유효하게 보므로, 늦게 병합되는 쪽이 이기고 다른 파트 잡이 존재하지 않는 stage 를 참조하게 되어 안 돈다 |
| `variables` (전역) | 공용 변수(`AXMAP_VERSION` 등)다. 잡 안의 `variables:` (잡 전용)는 파트 파일에 있어도 된다 — 여기서 막는 것은 **최상위** `variables:` 뿐이다 |

## 3. 🔴 옮긴 뒤 잡이 조용히 사라지지 않았는지 개수로 확인한다

옮기기 전에 뿌리 파일에서 자기 파트 잡 이름을 먼저 세어 두고,

```bash
grep -c '^backend:' .gitlab-ci.yml      # 옮기기 전
```

옮긴 뒤 두 파일의 합이 같은지 확인한다.

```bash
grep -c '^backend' ci/parts/backend.yml   # 옮긴 뒤 — 잡 이름이 backend: 로 시작하는 것 전부
```

숫자가 안 맞으면 잡이 어딘가에서 지워진 것이다. **개수가 조용히 줄어드는 것이
이 작업에서 가장 무서운 실패다** — 파이프라인은 초록인데 검사가 하나 빠진
채로 통과한다.

## 4. GitLab CI Lint 로 검사한다

```bash
curl -sS -X POST "https://lab.ssafy.com/api/v4/projects/1444066/ci/lint" \
  -H "PRIVATE-TOKEN: $GITLAB_TOKEN" -H "Content-Type: application/json" \
  --data "$(jq -Rs '{content:.}' .gitlab-ci.yml)"
# → "valid": true, "errors": [] 이어야 한다
```

`content` 로 보내는 뿌리 파일은 `include` 로 `ci/parts/*.yml` 을 끌어오므로,
**push 해서 그 파일들이 실제로 저장소에 있는 상태에서** 검사해야 의미가
있다. push 전 검사는 "파일이 없을 때도 안 죽는가" 만 본다 (`rules: exists:`
가 도는지의 시험이지, 옮긴 잡 내용의 시험이 아니다).

## 5. 왜 이렇게 하는가

파일이 하나면 파트 수만큼 사람이 같은 파일을 동시에 고치고, git 은 그 충돌을
알아서 풀지 못한다 — 사람이 매번 손으로 풀어야 하고, 위험한 줄(LFS 훅 우회
같은)을 잘못된 쪽으로 받을 여지가 생긴다. 파일을 파트 수만큼 쪼개면 애초에
같은 줄을 두 사람이 동시에 고칠 일이 없다. `include:rules:exists` 로 감싸는
것은 그 대가를 치른다 — 파트 폴더가 브랜치마다 있고 없고가 다르듯, 파트 CI
파일도 아직 없는 브랜치가 있을 수 있고, 없다고 파이프라인 전체가 죽으면 안
되기 때문이다.

---

## 🔴 함정 — 파트 파일을 비워 두면 CI 전체가 죽는다 (2026-09-09 실측)

주석만 있는 `.yml` 은 YAML 이 **빈 값(null)** 으로 읽는다. GitLab 은 그것을 이렇게 거부한다.

```
Included file `ci/parts/backend.yml` does not have valid YAML syntax!
```

**무서운 것은 실패하는 방식이다.** 설정이 무효면 GitLab 은 **잡을 하나도 만들지 않고**
파이프라인만 `failed` 로 찍는다. 화면에 실패한 잡이 없어서 **원인을 찾을 단서가 안 남는다.**
MR `!462` 가 실제로 그렇게 죽었다 — 잡 0개, `yaml_errors: null`.

그래서 여섯 파일에 **숨은 잡**(이름이 점으로 시작하면 GitLab 이 잡으로 만들지 않는다)을
한 줄씩 넣어 두었다.

```yaml
.backend-part-placeholder: {}
```

**첫 잡을 이 파일로 옮겨 오면 그 줄은 지워도 된다.** 파일이 다시 비게 되면 넣어야 한다.

### 🔴 CI Lint 로는 이 오류를 못 잡는다

`POST /ci/lint` 에 파일 내용만 보내면 **`include:` 가 가리키는 파일을 가져오지 않는다.**
그래서 `valid: true` 가 나온다. 실제로 그렇게 통과한 뒤 파이프라인에서 죽었다.

**반드시 `ref` 를 함께 준다** — 그때만 저장소에서 include 파일을 실제로 읽는다.

```bash
curl -X POST -H "PRIVATE-TOKEN: $TOKEN" -H "Content-Type: application/json"   --data '{"content": "...", "dry_run": true, "ref": "<브랜치>"}'   "$HOST/api/v4/projects/$ID/ci/lint"
```

