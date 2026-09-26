# 운영 nginx 접속 기록에서 사용자 좌표 빼기 — S15P21E201-1697

> 🔴 **적용은 iOS 심사 뒤, 사람이 한다.** 운영 설정 변경이다. 이 폴더는 변경안과 절차만 둔다.

## 왜

「내 주변」·버스 도착 같은 화면이 사용자 좌표를 주소의 물음표 뒤(쿼리)에 실어 보낸다.
운영 nginx 는 기본 형식(combined)으로 **요청 줄을 통째로** 적으므로 좌표가 접속 기록에 쌓인다.
처리방침의 「좌표는 저장하지 않음」과 어긋난다. 이미 나간 앱(build 44)도 같은 요청을 보내므로 앱을 고치는 것만으로는 못 막는다.

## 무엇을 바꾸나

| | 지금 | 바꾼 뒤 |
|---|---|---|
| 이름이 lat · lng · lon · latitude · longitude 로 **끝나는** 칸이 든 요청(대소문자 무관) | `GET /api/v1/places/nearby?lat=35.15&lng=129.16&limit=20 HTTP/1.1` | `GET /api/v1/places/nearby?LOCATION_REDACTED HTTP/1.1` |
| 〃 — 길찾기 모양 | `GET /api/v1/routes/directions?originLat=…&destLng=…&mode=WALK HTTP/1.1` | `GET /api/v1/routes/directions?LOCATION_REDACTED HTTP/1.1` |
| 그 밖의 요청 | 그대로 | **한 글자도 안 바뀐다** |

- 경로 이름으로 고르지 않고 **쿼리의 칸 이름**으로 고른다. 운영 기록에 좌표가 실린 경로가 아홉 곳이었다 — 경로를 적어 두면 새 화면이 생길 때마다 빠진다.
- 🔴 **S15P21E201-1718** — 처음 판(-1697)은 칸 이름이 그 다섯과 **정확히 같을 때만** 걸려서 길찾기의 `originLat` · `originLng` · `destLat` · `destLng` 가 기록에 남았다(프론트 세션 발견). 이름이 그 다섯으로 **끝나면** 걸리게 넓혔다. 대가로 좌표가 아닌데 그렇게 끝나는 이름(`plat` · `salon` …)도 가려진다 — 그 요청은 쿼리만 안 남고 경로 · 상태는 남는다. 2026-09-26 백엔드의 요청 칸 가운데 그렇게 끝나는 것은 전부 좌표였다(`lat` · `lng` · `lon` · `originLat` · `originLng` · `destLat` · `destLng`).
- 경로 · 방법 · 응답 코드 · 시각 · 크기는 그대로 남는다. 「내 주변이 몇 번 불렸나」는 계속 셀 수 있다.
- 파일 두 곳을 건드린다: `conf.d/access-log-privacy.conf` 를 **넣고**, `nginx.conf` 의 `access_log` 한 줄을 **주석으로 돌린다.**

> 🔴 **두 번째를 빼먹으면 요청마다 두 줄이 적힌다 — 좌표가 든 옛 모양 한 줄 + 뺀 모양 한 줄.** 같은 층(http)에 `access_log` 가 둘이면 nginx 는 둘 다에 적는다. 로컬에서 실제로 그렇게 적히는 것을 확인했다(아래 「확인한 것」).

## 적용 (심사 뒤)

운영 호스트(`ubuntu@j15e201.p.ssafy.io`)에서. 저장소 파일은 먼저 호스트의 `/tmp` 로 옮긴다(`scp infra/nginx/access-log-privacy.conf <호스트>:/tmp/`).

```bash
# 0. 되돌릴 때 쓸 사본
sudo cp /etc/nginx/nginx.conf /etc/nginx/nginx.conf.bak-1697

# 1. 새 형식 넣기
sudo install -m 644 /tmp/access-log-privacy.conf /etc/nginx/conf.d/access-log-privacy.conf

# 2. nginx.conf 의 옛 기록 줄(40행, 탭 들여쓰기)을 주석으로
sudo sed -i 's|^\taccess_log /var/log/nginx/access.log;$|\t# access_log /var/log/nginx/access.log;  # S15P21E201-1697 — conf.d/access-log-privacy.conf 로 옮김|' /etc/nginx/nginx.conf

# 3. access_log 가 딱 하나(conf.d 의 것)만 살아 있는지 — 주석 아닌 줄이 한 줄이어야 한다
grep -nE '^\s*access_log' /etc/nginx/nginx.conf /etc/nginx/conf.d/*.conf /etc/nginx/sites-enabled/*
#   → conf.d/access-log-privacy.conf 한 줄 + sites-enabled/default 의 `access_log off;`(설문) 한 줄

# 4. 검사하고 다시 읽기 — reload 는 연결을 끊지 않는다
sudo nginx -t && sudo systemctl reload nginx
```

## 확인

```bash
curl -s -o /dev/null "https://j15e201.p.ssafy.io/api/v1/weather?lat=35.1796&lon=129.0756&date=$(date +%F)"
curl -s -o /dev/null "https://j15e201.p.ssafy.io/api/v1/routes/directions?originLat=35.1796&originLng=129.0756&destLat=35.1587&destLng=129.1604&mode=WALK"
curl -s -o /dev/null "https://j15e201.p.ssafy.io/api/actuator/health"
sudo tail -n 20 /var/log/nginx/access.log | grep -c 'LOCATION_REDACTED'                                   # 2 이상
sudo tail -n 20 /var/log/nginx/access.log | grep -ciE '[?&][^=& ]*(lat|lng|lon|latitude|longitude)='      # 0
sudo tail -n 20 /var/log/nginx/access.log | grep -c '/api/actuator/health'                                # 1 (두 번 적히면 3번을 다시 본다)
```

## 되돌리기

```bash
sudo rm /etc/nginx/conf.d/access-log-privacy.conf
sudo cp /etc/nginx/nginx.conf.bak-1697 /etc/nginx/nginx.conf
sudo nginx -t && sudo systemctl reload nginx
```

## 확인한 것 (2026-09-26, 이 PC 로컬 — 운영과 같은 nginx 1.24)

운영과 같은 모양(40행 주석 + `conf.d` 포함 + 설문 `access_log off`)으로 띄워 요청 열한 개를 보냈다. `nginx -t` 통과.

| 보낸 요청 | 적힌 줄 |
|---|---|
| `GET /api/v1/places/nearby?lat=35.15&lng=129.16&limit=20` | `GET /api/v1/places/nearby?LOCATION_REDACTED` |
| `GET /api/v1/weather?date=…&lat=35.1796&lon=129.0756` (좌표가 첫 칸이 아님) | `GET /api/v1/weather?LOCATION_REDACTED` |
| `GET /api/v1/transit/nearby-bus-arrivals?LAT=…&LNG=…` (대문자) | `…?LOCATION_REDACTED` |
| `GET /x?latitude=1` · `GET /x?lng=` (빈 값) · `GET …/now?radius=500&longitude=…` | 셋 다 `?LOCATION_REDACTED` |
| `GET /api/v1/stories?limit=20` · `GET /city3d/index.html` | **그대로** |

**-1718 에서 더 본 것** (같은 모양으로 다시 띄워 요청 열두 개, `nginx -t` 통과):

| 보낸 요청 | 적힌 줄 |
|---|---|
| `GET /api/v1/routes/directions?originLat=…&originLng=…&destLat=…&destLng=…&mode=WALK` | `…?LOCATION_REDACTED` |
| `GET /x?mode=WALK&destLng=129.2` (좌표가 첫 칸이 아님) · `GET /x?USERLATITUDE=1` (대문자) | 둘 다 `?LOCATION_REDACTED` |
| `GET /x?latency=5&format=json` (이름에 lat 이 들었지만 끝이 아님) · `GET /x?q=lat&note=abclat` (값에만 lat) | **그대로** |
| `GET /x?plat=1` (좌표가 아닌데 lat 으로 끝남) | `?LOCATION_REDACTED` — 넓힌 대가, 위 설명 |
| `POST /api/v1/places/abc/visit-verifications` (좌표는 본문) | 그대로 — 본문은 원래 안 적힌다 |
| `GET /survey?lat=…` | 안 적힌다(원래 `access_log off`) |

40행을 살려 둔 채로 다시 띄우면 좌표 요청 하나가 **두 줄**(옛 모양 + 뺀 모양), 좌표 없는 요청도 같은 줄 두 번 적혔다.

## 지금 쌓여 있는 옛 기록 (2026-09-26 01시 KST, 읽기로만 셈)

기록은 매일 밤 돌리고 **14일 치**를 남긴다(`/etc/logrotate.d/nginx` — `daily` · `rotate 14`). 지우지 않아도 적용한 날부터 14일 뒤면 옛 좌표 줄은 모두 사라진다. **지금 지울지는 사용자 결정이다.**

| 파일 | 날짜(UTC) | 좌표 든 줄 |
|---|---|---|
| access.log.14.gz · .13.gz | 9/11 · 9/12 | 0 · 0 |
| access.log.12.gz · .11.gz | 9/13 · 9/14 | 25 · 20 |
| access.log.10.gz · .9.gz · .8.gz · .7.gz | 9/15 ~ 9/18 | 195 · 296 · 184 · 390 |
| access.log.6.gz · .5.gz · .4.gz | 9/19 ~ 9/21 | 27 · 55 · 195 |
| access.log.3.gz · .2.gz · .1 | 9/22 ~ 9/24 | 113 · 99 · 140 |
| access.log | 9/25 (16:30 UTC 까지) | 22 |

경로별(14일 합 1,761줄): 날씨 1,234 · 내 주변 396 · 버스 도착 43 · 지금 갈 곳(옛 GET) 3 · 날씨 예보 · 정류장 · 내 주변 거르기 · 장소 목록 각 1,
그리고 웹 브라우저가 본 요청 앞에 보내는 사전 확인(OPTIONS — 다른 주소로 불러도 되나 묻는 요청. 같은 주소를 싣는다) 내 주변 75 · 날씨 6. 새 형식은 OPTIONS 도 가린다.

🔴 **위 1,761줄은 칸 이름이 정확히 좌표일 때만 센 것이다.** 끝이름으로 다시 세면(2026-09-26 03시 KST, 읽기로만) **길찾기 `GET /api/v1/routes/directions` 333줄**이 더 있다(`originLat` 등). 처음 판을 그대로 적용했다면 이 줄들은 계속 좌표째 쌓였다.

- **날씨(예보 포함) 1,235줄 가운데 1,218줄은 사용자 위치가 아니다** — 앱이 부산시청 한 점(35.1796, 129.0756)으로 고정해 보낸다. 나머지 17줄(9/13~9/17)은 다른 점이다.
- 내 주변 · 버스의 좌표는 약 100m 단위로 뭉개면 서로 다른 점이 15곳이다.
- 리퍼러(어느 화면에서 왔나) 칸에 좌표가 든 줄은 0.

### 🔴 이 변경으로 못 막는 것 — `error.log`

nginx 는 오류 기록(`error.log`)에 요청 줄을 통째로 붙이고, 그 형식은 바꿀 수 없다. 14일 치에 좌표가 든 줄이 54줄 있다 —
백엔드 연결 끊김 25 · 요청 속도 제한 17 · 백엔드 꺼짐(배포 중) 8 · 응답 전에 끊김 4. 모두 백엔드가 멈췄거나 몰렸을 때만 생긴다.
막으려면 오류 기록 수준을 `crit` 로 올려야 하는데 그러면 위 네 가지 진단이 모두 사라진다. **남겨 두는 것을 권한다** — 14일이면 사라지고 드물다.
