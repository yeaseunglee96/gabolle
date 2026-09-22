# 서버 인프라 & CI/CD 셋업 — EC2 (`j15e201.p.ssafy.io`)

**S15P21E201-531.** 실제 백엔드/프론트엔드 코드가 나오기 전에, EC2 서버 인프라와
배포 파이프라인이 정상 동작하는지 미리 구축·검증한 기록이다. 코드가 완성되면
이 문서의 "연습용 리소스" 자리를 실제 서비스로 바꾸기만 하면 된다.

참고 자료: 15기 코치세션 PDF
(`프로젝트 시작 전 꼭 챙겨야 할 서버 셋업과 CI/CD 셋업_최민석`).

---

## 1. 서버 정보

| | |
|---|---|
| 도메인 | `j15e201.p.ssafy.io` |
| OS | Ubuntu 24.04 LTS (noble) |
| 접속 계정 | `ubuntu` |
| 접속 방법 | SSH(원격 서버에 암호화 통신으로 접속하는 프로토콜), PEM 키 사용 — MobaXterm 또는 `ssh -i 키파일 ubuntu@j15e201.p.ssafy.io` |

> 🔴 **PEM 키 파일은 절대 커밋하거나 외부에 공유하지 않는다.** 유출되면 서버가
> 통째로 탈취된다.

---

## 2. 방화벽 (UFW)

**UFW**(Uncomplicated FireWall — 리눅스에서 어떤 네트워크 트래픽을 통과시킬지
정하는 방화벽 관리 도구)로 아래 포트만 열어 두었다.

| 포트 | 용도 |
|---|---|
| 22 | SSH |
| 80 | HTTP (443으로 리다이렉트됨) |
| 443 | HTTPS |
| 8081 | Jenkins 웹 UI |

```bash
sudo ufw allow 80
sudo ufw allow 443
sudo ufw allow 8081
sudo ufw reload
```

---

## 3. Nginx + HTTPS

**Nginx** — 정적 파일 서빙, 리버스 프록시(외부 요청을 내부 포트로 중계하는 것),
SSL/TLS 적용을 담당하는 웹 서버.

```bash
sudo apt update
sudo apt install nginx -y
```

**Certbot** — Let's Encrypt 무료 SSL/TLS 인증서를 자동으로 발급·적용해주는 도구.
Ubuntu 24.04부터 패키지명이 `python-certbot-nginx`가 아니라
**`certbot` + `python3-certbot-nginx`** 로 바뀌었다.

```bash
sudo apt-get install certbot python3-certbot-nginx -y
sudo certbot --nginx -d j15e201.p.ssafy.io
```

인증서 만료일: **2026-11-30** (Certbot이 자동 갱신 타이머를 심어 둔다).

### 리버스 프록시 설정 위치 — 실수하기 쉬운 지점

`/etc/nginx/sites-available/default` 에는 `server` 블록이 **3개** 있다.

1. `listen 80 default_server; server_name _;` — 기본 HTTP 서버 (여기 넣으면 안 됨)
2. `listen 443 ssl; server_name j15e201.p.ssafy.io;` — **Certbot이 만든 실제
   HTTPS 서버, `/api/` 프록시는 반드시 여기 넣는다**
3. `listen 80; server_name j15e201.p.ssafy.io; return 301 https://...` —
   HTTP→HTTPS 리다이렉트 (손댈 필요 없음)

2번 블록의 `location / { ... }` **위쪽**에 추가:

```nginx
location /api/ {
    proxy_pass http://localhost:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

```bash
sudo nginx -t              # 문법 검증
sudo systemctl reload nginx
```

> 🔴 **`proxy_pass` 끝에 슬래시(`/`)를 붙이면 안 된다 — 붙이면 `/api` 접두사
> 자체가 사라진다.** `proxy_pass http://localhost:8080/;`처럼 URI 부분이 있는
> 채로 슬래시로 끝나면, nginx는 `location`이 매칭한 접두사(`/api/`)를 그
> 슬래시로 **치환**한다. 즉 외부의 `/api/v1/auth/signup`이 백엔드에는
> `/v1/auth/signup`으로 도착한다. 실제로 이 문서엔 슬래시 없는 버전이 계속
> 적혀 있었는데, 서버에 배포된 실제 설정에는 슬래시가 붙어 있었던 적이 있다
> (S15P21E201-581·jaehyeon 리포트 — 회원가입 API가 항상 403/401이 나던 원인).
> 백엔드 컨트롤러가 `/api/v1/...`을 그대로 기대하기로 정해졌으니(팀 결정),
> **`/api` 접두사는 백엔드까지 보존돼야 한다** — 그래서 슬래시를 뺀다.
>
> 단, `actuator/health`는 예외다. Spring Boot Actuator는 `/actuator/health`에만
> 매핑돼 있고 `/api` 접두사를 모른다. 그래서 `/api/` 블록보다 먼저 매칭되는
> **정확히 일치하는(`location =`) 블록**을 따로 둬서, 이 경로만 접두사를 벗겨
> 내부로 전달한다:
>
> ```nginx
> location = /api/actuator/health {
>     proxy_pass http://localhost:8080/actuator/health;
>     proxy_http_version 1.1;
>     proxy_set_header Host $host;
>     proxy_set_header X-Real-IP $remote_addr;
>     proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
>     proxy_set_header X-Forwarded-Proto $scheme;
> }
> ```
>
> `location =`(정확히 일치)는 접두사 매칭인 `location /api/`보다 항상 먼저
> 선택되므로, 두 블록의 순서는 상관없다.

### 요청 비율 제한 (rate limiting) — S15P21E201-581

HTTPS는 적용돼 있었지만, 짧은 시간에 대량 요청이 들어오는 것을 막는 장치가
없었다. `/etc/nginx/sites-available/default` 맨 위(파일 전체가 `http {}`
컨텍스트 안에 include되므로 `server {}` 밖에 있으면 http 컨텍스트다)에 zone을
정의하고, `/api/` location 안에 적용했다.

```nginx
# 파일 맨 위, 아무 server {} 블록 밖에
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;

server {
    ...
    location = /api/actuator/health {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://localhost:8080/actuator/health;
        ...
    }

    location /api/ {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://localhost:8080;
        ...
    }
}
```

- `rate=10r/s`: IP 하나당 초당 10개 요청까지 정상 처리
- `burst=20 nodelay`: 순간적으로 몰리는 20개까지는 즉시 처리하고, 그 이상은
  큐잉 없이 바로 거부(기본 `503`)한다 — API 응답성을 위해 지연 큐잉 대신
  즉시 거부를 택함

**검증**: 정상적인 순차 요청(초당 몇 건 수준)은 전부 `200`으로 그대로
통과했다. 40개 요청을 **동시에** 보내자 일부가 `503`으로 거부되는 것을
확인했다 (`curl ... & done; wait`로 병렬 발사 — 순차 반복문은 각 요청 사이
TLS 핸드셰이크 시간 때문에 초당 10건을 안 넘어서 재현 안 됨).

**추가 수정 (2026-09-03, S15P21E201-581, jaehyeon 리포트)**: 회원가입 API가
계속 403/401이 나던 원인이 바로 위 `/api` 접두사 문제였다. `/api/` 슬래시를
빼고 `/api/actuator/health` 예외 블록을 추가한 뒤 실제로 확인했다:
`/api/actuator/health` → `200` (그대로 유지), `POST /api/v1/auth/signup`
(빈 JSON) → `500`(경로 문제는 해결됐고, 이제 백엔드의 프로필/DB 설정
문제만 남음 — Jenkins 환경변수 주입 작업으로 이어짐).

### 업로드 크기 상한 — 사진이 한 장도 안 올라가던 원인 (2026-09-13)

nginx 는 요청 본문 크기에 기본 상한 **1MB** 를 건다(`client_max_body_size` —
**이보다 큰 요청은 받지 않고 끊는다는 설정**). 앱이 사진을 1600px 로 줄여
보내도 보통 그보다 커서, **업로드가 전부 `413`(Payload Too Large)으로 막혔다.**

🔴 **이 실패는 백엔드 로그에 아무것도 안 남긴다.** `413` 을 내는 것이 nginx 라
요청이 Spring 에 닿지도 않는다. 그래서 *"로그를 봐도 원인이 없다"* 가 됐고,
사진 저장소가 비어 있던 것도 「고장」이 아니라 **「닿은 적이 없음」**이었다.
운영 접근 로그 실측에서 파일이 실린 요청 6건이 전부 413 이었다
(2026-09-16, yeaseung-lee).

설정은 `server {}` 밖 http 컨텍스트에 둔다 — 바로 위 요청 비율 제한과 같은 자리다.

```bash
sudo tee /etc/nginx/conf.d/upload-size.conf >/dev/null <<'EOF'
client_max_body_size 5m;
EOF
sudo nginx -t && sudo systemctl reload nginx
```

🔴 **지금 서버에 걸려 있는 값과 같다. 상한을 바꾸는 것이 아니라 잃지 않으려는
것이다.** 이 한 줄은 2026-09-13 11:49 에 서버에서 손으로 만들어졌고 **저장소에는
없었다** — 서버를 다시 만들면 1MB 로 돌아가고 같은 사고가 그대로 재현된다.

**상한은 네 층에 따로 걸려 있다.** 🔴 **셋인 줄 알기 쉬운데 넷이다** —
`spring.servlet.multipart` 가 사진용으로 잡혀 있지만 **multipart 요청 전부에** 걸린다
(S15P21E201-1275 에서 동영상 상한을 넣어 놓고 그 값이 한 번도 안 쓰이는 것을 보고 알았다).

| | 층 | 값 | 어디에 적혀 있나 |
|---|---|---|---|
| 안쪽 | 앱 | 고를 때 30MB · **줄인 뒤 3MB** | `frontend/src/social/imageResize.ts` 의 `MAX_PICK_BYTES`·`MAX_UPLOAD_BYTES` (`front/dev` 에 있다 — `back/dev` 의 `frontend/` 는 자리표시자다) |
| ↓ | 도메인 · 사진 | 3MB | `UploadedImage.MAX_BYTES` (코드에 박혀 있다) |
| ↓ | 도메인 · 동영상 | **3MB** | `gabolle.storage.video.max-bytes` (설정값 — 실측 뒤 바뀐다) |
| ↓ | **Spring multipart** | 파일 **4MB** · 요청 **4.5MB** | `backend/src/main/resources/application.properties` |
| 바깥 | **nginx** | **5MB** | 이 절 |

## 🔴 규칙 — 안쪽이 바깥보다 작아야 한다. **같아도 안 된다**

```
3MB  <  4MB  <  4.5MB  <  5MB
```

**같으면 바깥 층이 먼저 자르고, 안쪽 층이 준비한 설명 있는 오류가 한 번도 안 쓰인다.**
사용자는 「올리기 실패」만 보고 얼마까지 되는지 영영 모른다.

가장 나쁜 것은 **nginx 가 자르는 경우**다 — 요청이 Spring 에 닿지도 않아
**백엔드 로그에 아무것도 안 남는다.** 2026-09-13 에 실제로 그랬고 원인을 찾는 데 사흘 걸렸다.

🔴 **그래서 상한을 올릴 때는 네 층을 함께, 여유를 남기고 올린다.** 한 층만 올리면
그 층은 한 번도 안 불린다 — 값을 고쳤는데 아무것도 안 바뀌는 상태가 되고, 그때
설정 파일만 보면 **바뀐 줄 안다.**

---

## 4. Docker

이 EC2에는 **Docker가 이미 설치되어 있었다** (버전 29.7.2, GitLab Runner 저장소도
이미 등록되어 있었음 — 누가 셋업해 뒀는지는 불명).

```bash
docker --version
sudo systemctl status docker
```

권한 오류(`permission denied ... docker.sock`)가 나면, 계정이 `docker` 그룹에
없는 것이다.

```bash
sudo usermod -aG docker $USER
newgrp docker
docker ps -a   # sudo 없이 확인
```

### 배포 흐름 검증 (연습용 — 실제 코드 없이)

`nginx:alpine` 기반으로 정적 JSON을 `/api/hello`에서 응답하는 컨테이너를 만들어
"Dockerfile 작성 → 빌드 → 실행 → Nginx 리버스 프록시 연결" 전체 흐름을 검증했다.

```bash
docker build -t hello-test .
docker run -d --name hello-test -p 8080:8080 hello-test
curl -s http://localhost:8080/api/hello | jq .
```

외부에서 `https://j15e201.p.ssafy.io/api/hello` 로 접속해 같은 응답이 나오면
Nginx 리버스 프록시까지 정상 연결된 것이다.

> **실제 백엔드가 생기면**: `hello-test` 컨테이너 자리(8080 포트)에 실제 백엔드
> 컨테이너를 올리면 된다. 위 Nginx `/api/` 설정은 그대로 재사용 가능하다.

---

## 5. Jenkins (CI/CD)

**Jenkins** — 코드가 바뀔 때마다 자동으로 빌드·테스트·배포까지 이어주는 도구.
Docker 컨테이너로 띄웠다.

```bash
docker volume create jenkins_home

docker run -d \
  --name jenkins \
  --restart unless-stopped \
  -p 127.0.0.1:8081:8080 \
  -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --group-add 987 \
  -e JENKINS_OPTS="--prefix=/jenkins" \
  jenkins/jenkins:lts
```

- 컨테이너 내부는 8080(Jenkins 기본 포트)이지만, 앱 배포용 8080과 겹치지 않게
  예전엔 호스트 쪽을 8081로 열었었다. **2026-09-03부터 `127.0.0.1:8081`로 막고
  nginx 뒤로 옮겼다** — 아래 "443 서브패스 이전" 참고.
- 접속: `https://j15e201.p.ssafy.io/jenkins/`
- 초기 비밀번호: `docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword`
- `/var/run/docker.sock` 을 마운트해, 파이프라인이 호스트의 Docker를
  직접 호출(`docker build`/`docker run`)할 수 있게 했다.
- `--group-add 987` — 호스트의 `docker` 그룹 GID(`getent group docker`로 실측,
  서버마다 다를 수 있다). 이게 없으면 컨테이너 안의 `jenkins` 사용자가
  마운트된 `docker.sock`에 `permission denied`를 받는다.

설치 후 "Install suggested plugins" → **로그인 필수인 관리자 계정 생성**까지
마쳤다 (로그인 없이 쓰는 상태로 두면 안 된다).

### 🔴 컨테이너를 재생성할 때마다 사라지는 것 셋 — 매번 다시 해야 한다

**2026-09-03, 443 서브패스로 옮기며 두 번 재생성했고 세 번 다 이걸 잊어서 배포가
막혔다.** `docker run`이 만드는 이미지 레이어 밖의 상태는 재생성 순간 전부
초기화된다. `jenkins_home`(named volume)에 안 들어가는 것들이라 위 `docker run`
명령에 넣지 않으면 아무리 여러 번 재생성해도 계속 빠진다.

1. **`docker` CLI 자체** — `jenkins/jenkins:lts` 이미지엔 기본으로 없다.
   `docker.sock`은 마운트돼 있어도 명령어가 없으면 `docker: not found`.
   ```bash
   docker exec -u root jenkins apt-get update
   docker exec -u root jenkins apt-get install -y docker.io
   ```
2. **`docker.sock` 그룹 권한** — 위 `--group-add 987`로 `docker run` 시점에
   넣어야 한다. 이미 떠 있는 컨테이너에 `usermod`로 나중에 추가해도 Jenkins
   프로세스가 이미 그 그룹 없이 시작돼서 반영이 안 된다 — 컨테이너를 다시
   만들어야 한다.
3. **`local-route-personalization_data_net` 네트워크 연결** — `backend`
   컨테이너와 이름으로 통신하려면 필요하다. 컨테이너 인스턴스에 붙는 것이라
   재생성마다 다시 붙여야 한다:
   ```bash
   docker network connect local-route-personalization_data_net jenkins
   ```

**재생성 뒤에는 이 셋 다 됐는지 `docker exec jenkins docker ps`로 한 번에
확인한다** — 에러 없이 (빈 목록이라도) 나오면 1·2번은 된 것이고, 3번은
`backend-deploy`를 한 번 돌려서 Health Check가 `000`(연결 자체 실패)이 아니라
실제 응답 코드를 받는지로 확인한다.

### GitLab Webhook 연동

1. Jenkins에 **Generic Webhook Trigger** 플러그인 설치
   (Manage Jenkins → Plugins → Available plugins)
2. Pipeline Job 생성 → Configure → Triggers 탭 → **Generic Webhook Trigger** 체크
   → Token 입력 (예: `hello-test-deploy`)
3. GitLab 저장소 → **Settings → Webhooks** (Maintainer 권한 필요할 수 있음)
   → URL 등록:
   ```
   https://j15e201.p.ssafy.io/jenkins/generic-webhook-trigger/invoke?token=<TOKEN>
   ```
   → Trigger: `Push events`
4. GitLab의 **Test → Push events** 버튼으로 테스트 →
   `Hook executed successfully: HTTP 200` 확인
5. Jenkins Build History에 **"Generic Cause"** 로 시작된 빌드가 자동 생성되면
   연동 성공.

> 🔴 **파이프라인 스크립트 안에서 `localhost`는 Jenkins 컨테이너 자기 자신을
> 가리킨다.** 다른 컨테이너(예: 8080의 앱)와는 별개의 네트워크 네임스페이스라서,
> `curl http://localhost:8080/...`을 파이프라인 안에서 호출하면 Jenkins 자신의
> 웹 UI(로그인 페이지)가 응답한다. 실제 배포 스크립트를 작성할 때는 컨테이너
> 이름(`http://backend:8080/...`, 같은 도커 네트워크에 있을 때)으로 접근해야 한다.

### Jenkins UI를 443 서브패스로 옮김 (2026-09-03, S15P21E201-201)

**전**: `http://j15e201.p.ssafy.io:8081` — 평문(HTTP), UFW로 8081을 인터넷에
직접 열어 둠. Jenkins 컨테이너에 `docker.sock`이 마운트돼 있어 뚫리면 EC2 호스트
전체(DB 비밀번호·JWT 시크릿·배포 SSH 키 포함)가 뚫리는 구조였는데, 로그인이
평문이고 rate limit도 없었다.

**후**: `https://j15e201.p.ssafy.io/jenkins/` — nginx 뒤(443)로 들어가 TLS를
타고, UFW는 22·80·443 셋만 남았다.

바꾼 것 셋:

1. Jenkins 컨테이너: `-p 127.0.0.1:8081:8080`(loopback만) +
   `JENKINS_OPTS="--prefix=/jenkins"` — 위 `docker run` 참고
2. nginx 443 서버 블록에 `location /jenkins/` 추가 (`location /` **위쪽**,
   `/etc/nginx/sites-available/default`):
   ```nginx
   location /jenkins/ {
           proxy_pass http://127.0.0.1:8081/jenkins/;
           proxy_http_version 1.1;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
           proxy_redirect off;
   }
   ```
3. Jenkins 자체 설정 — Manage Jenkins → System → Jenkins Location → Jenkins URL을
   `https://j15e201.p.ssafy.io/jenkins/`로 변경 (끝 슬래시 포함)

GitLab Webhook 2개(`backend-deploy`, `infra-personalization-deploy`)의 URL도
새 경로로 바꿨다 — 위 "GitLab Webhook 연동" 참고. 전환 뒤 GitLab **Test → Push
events**로 실제 Jenkins Build History에 빌드가 생기는 것까지 확인했다.

> 🔴 **컨테이너 재생성이 두 번 필요했다** — 처음엔 `--prefix`만 넣고 만들었는데
> "컨테이너를 재생성할 때마다 사라지는 것 셋" 중 docker CLI가 없어서 빌드가
> 죽었고, 다시 설치한 뒤 이번엔 `docker.sock` 권한이 없어서 또 죽었다.
> `--group-add`는 `docker run` 시점에만 먹으므로 두 번째 재생성이 필요했다.
> 이 문서의 `docker run` 명령에는 처음부터 셋 다 넣어 뒀으니, **다음에 재생성할
> 사람은 이 문서의 명령을 그대로 쓰면 두 번 겪지 않는다.**

---

## 6. 연습용 리소스 — 정리 완료 (S15P21E201-531·574·575·583)

`hello-test`는 실제 백엔드(`back/dev`, Spring Boot)로 교체됐다. 지금 8080 포트에는
`backend` 컨테이너가 떠 있고, Jenkins Job `backend-deploy`는 저장소의
`backend/Jenkinsfile`을 읽는 "Pipeline script from SCM" 방식이며, GitLab
Webhook은 `back/dev` 브랜치로 제한되어 있다. 빌드 성공/실패는 MatterMost
E201봇채널로 자동 알림된다.

**2026-09-03, 완전히 정리함** — `backend-deploy`·`infra-personalization-deploy`가
둘 다 실제로 도는 것까지 확인된 뒤, 연습용 잔재를 전부 지웠다.

- Jenkins Job `hello-test-pipeline` 삭제 — GitLab Webhook이 "All branches"로
  걸려 있어서 이번 세션에 올린 수십 개 브랜치 push마다 반응해 `#350`까지
  불필요하게 쌓여 있었다
- GitLab Webhook의 `hello-test-deploy` 토큰 항목 삭제
- EC2의 `~/hello-test/` 디렉터리 삭제 (여기서 배운 내용은 4절에 명령어까지
  전부 문서로 남아 있어서, 파일 자체가 없어도 기록은 살아있다)

---

## 7. 프론트엔드 — Expo 웹 미리보기 (S15P21E201-583)

`frontend/`는 React+TS 웹이 아니라 **Expo(React Native) 앱**이다 — 실제 iOS/
Android 배포는 EAS Build(Expo 클라우드)를 통하고, 우리 EC2와는 무관하다.
그래도 팀원들이 물리 디바이스 없이 브라우저로 바로 확인할 수 있도록, Expo의
웹 export 결과물만 우리 서버에서 Nginx로 서빙한다.

```bash
cd /opt/local-route/repository/frontend
docker build -t local-route-frontend .
docker run -d --name frontend --restart unless-stopped -p 3000:80 local-route-frontend
```

`frontend/Dockerfile`이 `npx expo export --platform web`으로 정적 번들을
만들고 `nginx:alpine`으로 서빙한다. expo-router는 클라이언트 사이드
라우팅이라, `frontend/nginx.conf`에 `try_files ... /index.html` fallback이
있어야 새로고침이나 직접 URL 접속에서 404가 안 난다.

메인 Nginx의 443 서버 블록에서 `location /`이 이 컨테이너(포트 3000)를
가리키도록 프록시한다 — `location /api/`와 같은 자리, 같은 방식이다.

```nginx
location / {
    limit_req zone=api_limit burst=20 nodelay;
    proxy_pass http://localhost:3000;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

**검증**: 서버에서 직접 빌드·실행 후 `https://j15e201.p.ssafy.io/`에서 실제
GABOLLE 화면(`<title>GABOLLE</title>`)과 임의 하위 경로의 라우팅 fallback을
확인했다.

### 자동 배포 — Jenkins Job `frontend-deploy` (S15P21E201-594)

> 🔴 **2026-09-04 — front/dev가 서버에 34커밋 밀려 있던 것을 실측으로 발견함.**
> 자동 배포 경로가 아예 없어서, 위 7절의 수동 `docker build`/`docker run`을
> 딱 한 번 한 뒤로는 아무도 다시 안 돌렸다. 라이브 이미지가 2026-09-03 02:40
> UTC 빌드였는데 그사이 오늘 머지된 내비게이션(-593)·i18n(-124) 등 34개
> 커밋이 화면에 하나도 안 보이는 상태였다. SSH로 최신 front/dev를 수동
> 재배포해 우선 복구하고(200 확인), backend-deploy(S15P21E201-575)와 같은
> 패턴으로 자동화를 추가한다.

`backend/Jenkinsfile`과 동일한 구조로 `frontend/Jenkinsfile`을 추가했다 —
Checkout(front/dev) → Build Image → Deploy → Health Check, MatterMost 알림
포함. 컨테이너는 `local-route-personalization_data_net`에 조인시켜서, Jenkins
컨테이너가 `http://frontend:80/`으로 이름 조회할 수 있게 했다(backend와 같은
이유 — 5절의 "localhost는 Jenkins 자기 자신" 함정 참고).

Jenkins Job `frontend-deploy`는 Pipeline script from SCM 방식으로 이미
만들었다 (Script Path `frontend/Jenkinsfile`, branch `front/dev`, Generic
Webhook Trigger token `frontend-deploy`).

**아직 안 된 것**: `frontend/Jenkinsfile`이 아직 `front/dev`에 없다 (MR
대기 중 — 머지되어야 이 Job이 실제로 돈다). GitLab Webhook 등록(URL을
`.../jenkins/generic-webhook-trigger/invoke?token=frontend-deploy`로,
Wildcard pattern `front/dev`로 제한)도 Maintainer 권한의 GitLab PAT가
있어야 해서 아직 안 걸었다 — "GitLab Webhook 연동" 절차를 그대로 따르면 된다.

---

## 8. 앞으로 남은 것

문서 5~13절(개인화 인프라 구축 가이드)은 전부 끝났다 (10절 Kafka는 팀 결정으로
범위 제외). `infra/personalization/Jenkinsfile`은 Jenkins Job
`infra-personalization-deploy`로 실제 연결됐고, GitLab Webhook(Push events,
Wildcard pattern `common/dev`)이 자동으로 트리거해 SHA 태깅 빌드·백업·배포·
헬스체크까지 전 과정이 초록으로 통과하는 것까지 확인됨 (S15P21E201-573).

실제로 아직 안 된 것:

- frontend GitLab Webhook 등록 (Jenkins Job `frontend-deploy`는 만들어짐, MR
  머지와 Webhook 등록이 남음 — 7절 "자동 배포" 참고, S15P21E201-594)
- ✅ **2026-09-03 — Jenkins UI를 443 서브패스로 옮김.** 8081은 UFW에서
  닫았고, 이제 22·80·443만 열려 있다. 5절 "Jenkins UI를 443 서브패스로 옮김"
  참고
- 개인화 인프라 자체의 세부 gap(모델/피처/DAG 롤백 미리허설 등)은 여기
  나열하지 않는다 — 항목이 늘어날 때마다 이 줄이 낡기 때문이다. 최신 목록은
  항상 [`infra/personalization/README.md`](../infra/personalization/README.md)
  13절을 본다

---

## 9. 개인화 인프라 — 별도 문서

PostgreSQL·Redis·MinIO·MLflow·Airflow로 구성된 개인화 추천 인프라는
`infra/personalization/`에 있고, 구축 절차와 **여기서 겪은 문제 해결 기록**은
[`infra/personalization/README.md`](../infra/personalization/README.md)에 정리했다
(S15P21E201-573·576).
