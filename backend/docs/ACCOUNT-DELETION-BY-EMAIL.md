# 이메일로 온 계정 삭제 요청 처리 — 운영자 런북

**S15P21E201-1647.** 앱에 로그인할 수 없는 사람(앱을 지웠거나 기기를 잃음)이 `gabolle.support@gmail.com` 으로
보낸 삭제 요청을 운영자가 대신 처리하는 절차다.

| | |
|---|---|
| 약속 | 공개 페이지(`/legal/account-deletion`)와 자동응답이 **「본인 확인이 끝나면 7일 이내에 삭제하고 결과를 알린다」** 고 적었다 |
| 누가 | 운영자(Maintainer). 서버에 SSH 로 들어갈 수 있는 사람 |
| 무엇으로 | 백엔드 이미지에 들어 있는 **실행기**(`OperatorAccountDeletionRunner`). 새 HTTP 삭제 주소를 만들지 않았다 |
| 지우는 범위 | 앱에서 본인이 「회원 탈퇴」 를 눌렀을 때와 **같은 코드**(`AccountDeletionService.delete`) |

---

## 0. 용어

| 말 | 뜻 |
|---|---|
| **실행기(Runner)** | *서버가 뜰 때 한 번 일하고 마치는 코드.* 프로퍼티(명령줄 인자)를 주었을 때만 만들어지고, 안 주면 평소 기동에 아무 영향이 없다 |
| **미리보기(dry run)** | *실제로는 아무것도 바꾸지 않고 「무엇이 지워질지」 만 보여 주는 실행* |
| **익명화** | *계정 행은 남기되 이름·로그인 수단·이메일 등 사람을 알아볼 값을 전부 비우는 것.* 남은 행은 누구인지 알 수 없다 |
| **SHA-256 해시** | *글자를 되돌릴 수 없는 64자 지문으로 바꾸는 것.* 이메일 원문 대신 이것만 처리 기록에 남긴다 |
| **로테이션(rotation)** | *오래된 백업 파일을 정해진 개수만 남기고 지우는 것* |

---

## 1. 본인 확인 — 지우기 전에 사람이 한다

이 실행기는 「이메일이 같은 계정」 을 찾아 지울 뿐 **그 사람이 진짜 주인인지는 모른다.** 그것은 운영자가 먼저 확인한다.

1. **요청 메일이 가입한 주소에서 왔는가.** 보낸 사람 주소를 그대로 미리보기(3절)에 넣어 본다. 계정이 나오면 「그 주소로 가입한 계정이 있다」 까지는 확인된 것이다
2. **그 주소로 회신한다.** 「삭제 요청이 맞으면 이 메일에 `삭제 동의` 라고 답장해 주세요. 지우면 되돌릴 수 없습니다」. 답장이 **같은 주소에서** 올 때까지 지우지 않는다
3. 답장이 오면 실행한다(4절). 접수일부터 **7일** 안이다 — 답장을 기다리는 시간도 세는 것이 안전하다. 3일째까지 답장이 없으면 한 번 더 안내한다
4. 실행 결과를 **같은 주소로** 알린다(5절)

> 🔴 **이 확인이 뚫리는 경우** — 남의 메일 계정을 쓸 수 있는 사람은 통과한다. 가볍게 넘길 수 있는 확인이 아니라 「이메일 소유 확인」
> 하나뿐이다. 계정이 장기 이용자의 것이거나(여행·기록이 많다) 요청이 이상하면 미리보기의 영향 수(여행·일정·기록)를 보고 **한 번 더
> 묻는다**(가입 시기·닉네임). 강도를 정하는 것은 팀 결정이다 — 아래 8절.

> 🔴 **Apple 「이메일 가리기」 로 가입한 사람** — 제공자 이메일이 `…@privaterelay.appleid.com` 이라 사용자가 보내는 주소와 다르다.
> 미리보기가 `NOT_FOUND` 로 나오면 가입할 때 쓴 Apple 릴레이 주소를 알려 달라고 회신한다(설정 → Apple ID → 로그인 및 보안).

---

## 2. 접수 기록

요청 하나마다 **요청 식별**(`request-ref`)을 붙인다 — 메일 수신일과 번호(`2026-09-27-메일1`). **이메일 주소·이름을 적지 않는다**
(실행기가 `@` 가 들어 있으면 거절한다).

---

## 3. 미리보기 (기본 — 아무것도 안 바뀐다)

서버에 SSH 로 들어가 백엔드가 쓰는 DB 접속값을 빌린다(장소 적재와 같은 방식, `PLACE-DATA-LOAD.md` 2절).

```bash
ssh -i $PEM ubuntu@j15e201.p.ssafy.io
set +o history                      # 이메일이 명령 기록에 남지 않게
docker inspect backend --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | grep -E '^(GABOLLE_DB_|SPRING_PROFILES_ACTIVE)' > /tmp/load.env
chmod 600 /tmp/load.env && wc -l /tmp/load.env     # 네 줄이면 정상
```

🔴 `/tmp/load.env` 에 DB 비밀번호가 들어 있다. **끝나면 지운다(6절).**

```bash
# 사용법: op_delete <이메일> <요청식별> <운영자> [execute]   — execute 를 안 주면 미리보기
op_delete() {
  local mode=false; [ "$4" = "execute" ] && mode=true
  local cid
  cid=$(docker run -d --network local-route-personalization_data_net --env-file /tmp/load.env \
    -e GABOLLE_JWT_SECRET=loader-only-throwaway-value-0123456789abcdef \
    local-route-backend:latest \
    --gabolle.operator.delete-account.email="$1" \
    --gabolle.operator.delete-account.request-ref="$2" \
    --gabolle.operator.delete-account.operator="$3" \
    --gabolle.operator.delete-account.execute=$mode) || return 1
  timeout 300 docker logs -f "$cid" 2>&1 \
    | sed -u '/대리 탈퇴 처리를 마쳤다\|APPLICATION FAILED TO START/q' \
    | grep -E '대리 탈퇴|Caused by|APPLICATION FAILED|IllegalArgument'
  docker rm -f "$cid" > /dev/null
}

op_delete 사용자@example.com 2026-09-27-메일1 이예승          # 미리보기
```

결과 줄:

```
대리 탈퇴 처리를 마쳤다 — 결과=DRY_RUN 계정=… 로그인수단=LOCAL 영향=[여행 3 · 일정 2 · 기록 5] 가리킨계정수=1
```

| 결과 | 뜻 | 할 일 |
|---|---|---|
| `DRY_RUN` | 계정 하나를 찾았다 | 1절 확인을 마쳤으면 4절 |
| `NOT_FOUND` | 그 이메일로 지금 쓸 수 있는 계정이 없다 | 이미 지웠거나 가입한 적이 없다. 요청자에게 사실을 알리고, 다른 가입 주소가 있는지 묻는다(Apple 릴레이 주의) |
| `AMBIGUOUS` | 한 이메일이 **서로 다른 계정 둘 이상**을 가리킨다 | **지우지 않았다.** 사람이 가려야 한다 — 요청자에게 닉네임·가입 시기를 물어 어느 계정인지 정한 뒤 개발자에게 넘긴다 |

---

## 4. 실행 — 지운다 (되돌릴 수 없다)

```bash
op_delete 사용자@example.com 2026-09-27-메일1 이예승 execute
```

`결과=DELETED` 가 나오면 끝이다. 지우기와 처리 기록이 **한 트랜잭션**(전부 되거나 전부 안 되거나)이라 「지웠는데 기록이 없는」
상태는 없다. 실패하면(`APPLICATION FAILED TO START`) 아무것도 지워지지 않았다 — 메시지를 읽고 고친 뒤 다시 한다.

- `request-ref`·`operator` 를 비우면 지우기 **전에** 멈춘다(기록이 빌 수 없다)
- 이미 지운 계정에 다시 해도 안전하다 — `NOT_FOUND` 가 나온다

---

## 5. 결과 회신 (같은 주소로)

`DELETED`: 「요청하신 계정과 데이터를 삭제했습니다. 백업에서는 최대 2주 안에 자동으로 사라집니다」 (7절이 근거).
`NOT_FOUND`·`AMBIGUOUS`: 3절 표대로.

---

## 6. 뒤처리

```bash
rm -f /tmp/load.env                 # DB 비밀번호가 든 파일
set -o history
```

처리 기록에는 이메일 원문이 없다. 「이 주소는 처리했나」 는 해시로 묻는다(DB 접속 후):

```sql
SELECT log_id, request_ref, operator_name, outcome, created_at
  FROM gabolle.operator_account_deletion_log
 WHERE email_sha256 = encode(sha256(convert_to(lower(btrim('사용자@example.com')), 'UTF8')), 'hex')
 ORDER BY log_id;
```

---

## 7. 백업에 남는 데이터 — 확인한 사실 (2026-09-26)

**DB 를 지워도 백업 파일에는 지우기 전 데이터가 남는다.** 얼마나 오래인지를 `infra/personalization/scripts/backup-postgres.sh` 와
서버의 실제 파일로 확인했다.

| 무엇 | 사실 |
|---|---|
| 언제 뜨나 | 매일 04:00 UTC(13:00 KST). `app_db`·`airflow_db`·`mlflow_db`·설문 DB |
| 얼마나 두나 | **일간 7개 + 주간 2개**(일요일 것). 그보다 오래된 것은 지운다 |
| 백업 서버 | `j15e201a` 로 `rsync --delete` 로 미러링한다 — **같은 파일이 같은 기간만** 남는다(따로 오래 두지 않는다) |
| 그래서 | 지운 계정의 데이터는 백업에서 **최대 약 2주(14일)** 안에 사라진다. 일간 7일, 주간은 일요일 것이 두 번 더 돌면 밀려난다 |

🔴 **로테이션 밖의 백업이 있다.** 손으로 뜬 `app_db_premigration_*.dump`·`app_db_pre_slope_*.dump` 는 이름이 로테이션 대상(`*_daily_*`·
`*_weekly_*`)이 아니라 **저절로 지워지지 않는다.** 배포·적재 전에 손으로 백업을 뜨면 **그 파일에 개인정보가 들어간다.**
그러므로 손 백업은 만든 날부터 **14일 안에 사람이 지운다**(`rm`). 이 런북을 만든 날 남아 있는 것:

| 파일(`/var/backups/local-route/postgres/`) | 만든 날 | 지울 날 |
|---|---|---|
| `app_db_premigration_20260925-2249.dump` | 2026-09-26 | 2026-10-10 이후 |
| `app_db_premigration_20260925-2310_b2.dump` | 2026-09-26 | 2026-10-10 이후 |
| `app_db_pre_slope_20260926-0124.dump` | 2026-09-26 | 2026-10-10 이후 |

(백업 서버에도 같은 파일이 미러링돼 있으니 서버에서 지우면 다음 동기화에 같이 사라진다.)

공개 문서에는 「백업에서는 최대 2주 안에 자동으로 순환 삭제된다」 라고 쓸 수 있다 — 개인정보 처리방침 보완(S15P21E201-1648)에서 옮긴다.

---

## 8. 아직 정하지 못한 것 (팀이 정한다 — 이 문서가 지어내지 않았다)

| | |
|---|---|
| 본인 확인 강도 | 지금은 「가입 이메일에서 온 요청 + 같은 주소의 회신」 이다. 더 세게(닉네임·가입 시기 묻기) 할지 |
| 처리 기록 보관 기간 | 이메일 해시·계정 번호·처리일·운영자가 남는다. 얼마나 둘지 정하지 않았다(기본: 무기한). 원문은 없다 |
| 답장 기한 | 3일째 재안내·7일째 종결로 적었지만 팀이 확정하지 않았다 |
| 요청자가 여러 명일 때 | 한 명이 운영하는 것으로 가정했다. 교대·백업 담당 |
