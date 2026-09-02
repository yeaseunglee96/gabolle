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
  -p 8081:8080 \
  -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  jenkins/jenkins:lts
```

- 컨테이너 내부는 8080(Jenkins 기본 포트)이지만, 앱 배포용 8080과 겹치지 않게
  **호스트 쪽은 8081**로 노출했다.
- 접속: `http://j15e201.p.ssafy.io:8081`
- 초기 비밀번호: `docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword`
- `/var/run/docker.sock` 을 마운트해 두어, 나중에 파이프라인이 호스트의 Docker를
  직접 호출(`docker build`/`docker run`)할 수 있게 준비해 두었다.

설치 후 "Install suggested plugins" → **로그인 필수인 관리자 계정 생성**까지
마쳤다 (로그인 없이 쓰는 상태로 두면 안 된다).

### GitLab Webhook 연동

1. Jenkins에 **Generic Webhook Trigger** 플러그인 설치
   (Manage Jenkins → Plugins → Available plugins)
2. Pipeline Job 생성 → Configure → Triggers 탭 → **Generic Webhook Trigger** 체크
   → Token 입력 (예: `hello-test-deploy`)
3. GitLab 저장소 → **Settings → Webhooks** (Maintainer 권한 필요할 수 있음)
   → URL 등록:
   ```
   http://j15e201.p.ssafy.io:8081/generic-webhook-trigger/invoke?token=<TOKEN>
   ```
   → Trigger: `Push events`
4. GitLab의 **Test → Push events** 버튼으로 테스트 →
   `Hook executed successfully: HTTP 200` 확인
5. Jenkins Build History에 **"Generic Cause"** 로 시작된 빌드가 자동 생성되면
   연동 성공.

> 🔴 **파이프라인 스크립트 안에서 `localhost`는 Jenkins 컨테이너 자기 자신을
> 가리킨다.** 다른 컨테이너(예: 8080의 앱)와는 별개의 네트워크 네임스페이스라서,
> `curl http://localhost:8080/...`을 파이프라인 안에서 호출하면 Jenkins 자신의
> 웹 UI(로그인 페이지)가 응답한다. 실제 배포 스크립트를 작성할 때는 호스트 IP나
> 공용 Docker 네트워크를 통해 접근해야 한다.

---

## 6. 연습용 리소스 — 정리 완료 (S15P21E201-531·574·575)

`hello-test`는 실제 백엔드(`back/dev`, Spring Boot)로 교체됐다. 지금 8080 포트에는
`backend` 컨테이너가 떠 있고, Jenkins Job `backend-deploy`는 저장소의
`backend/Jenkinsfile`을 읽는 "Pipeline script from SCM" 방식이며, GitLab
Webhook은 `back/dev` 브랜치로 제한되어 있다. 빌드 성공/실패는 MatterMost
E201봇채널로 자동 알림된다.

`~/hello-test/`(EC2 홈 디렉터리의 연습용 파일)는 참고용으로만 남겨뒀다 — 지워도
무방하다.

---

## 7. 앞으로 남은 것

- 실제 `frontend/` 코드/Dockerfile 작성 (현재는 placeholder 상태)
- 문서 13절(단계별 실행 일정과 완료 체크리스트) — 지금까지 한 것을 인수 조건에
  맞춰 최종 점검

`infra/personalization/Jenkinsfile`은 Jenkins Job `infra-personalization-deploy`로
실제 연결됐다 — GitLab Webhook(Push events, Wildcard pattern `common/dev`)이
자동으로 트리거하고, SHA 태깅 빌드·백업·배포·헬스체크까지 전 과정이 초록으로
통과하는 것까지 확인됨 (S15P21E201-573). 문서 11·12절(모니터링·백업·개인정보,
배포·롤백)도 완료됨.

---

## 8. 개인화 인프라 — 별도 문서

PostgreSQL·Redis·MinIO·MLflow·Airflow로 구성된 개인화 추천 인프라는
`infra/personalization/`에 있고, 구축 절차와 **여기서 겪은 문제 해결 기록**은
[`infra/personalization/README.md`](../infra/personalization/README.md)에 정리했다
(S15P21E201-573·576).
