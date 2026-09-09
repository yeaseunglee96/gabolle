# 부산 추천 설문 사진 — 출처와 라이선스

공개 설문 페이지에 쓰는 사진이다. **전부 자유 라이선스**(**저작권자가 미리 "누구나
써도 된다" 고 허락해 둔 것**)이고, 출처는 모두 Wikimedia Commons
(**위키백과가 쓰는 자유 이미지 창고**) 다.

라이선스 이름이 뜻하는 것은 이렇다.

| 라이선스 | 뜻 |
|---|---|
| **CC0** | 저작권을 아예 포기한 것. 표기 없이 아무렇게나 써도 된다 |
| **CC BY** | **촬영자 이름을 적으면** 상업적으로도 쓸 수 있다 |
| **CC BY-SA** | 이름을 적어야 하고, **사진 자체를 고쳐서 다시 배포할 때는** 같은 라이선스로 내야 한다 (사진을 그대로 페이지에 얹어 쓰는 것은 여기 해당하지 않는다) |

뒤에 붙는 숫자(2.0 · 3.0 · 4.0)는 판 번호일 뿐이고, 위 조건은 판이 달라도 같다.

---

## 5장

| 파일명 | 무엇 | 출처 URL (원본 페이지) | 촬영자 | 라이선스 | 내려받은 날 | 픽셀 크기 |
|---|---|---|---|---|---|---|
| `gwangan-bridge.jpg` | 광안대교 (낮, 항공) | https://commons.wikimedia.org/wiki/File:Gwangan_Bridge1.jpg | Glabb | CC BY-SA 3.0 | 2026-09-09 | 4000 × 2250 |
| `haeundae-beach.jpg` | 해운대 해수욕장 | https://commons.wikimedia.org/wiki/File:Haeundae_Beach_Busan_(45698772572).jpg | bryan... (Flickr 사용자 `bryansjs`) | CC BY-SA 2.0 | 2026-09-09 | 6720 × 4480 |
| `gwangalli-beach.jpg` | 광안리 해수욕장 | https://commons.wikimedia.org/wiki/File:Gwangalli_Beach.jpg | Chelsea Hicks | CC BY 2.0 | 2026-09-09 | 4752 × 3168 |
| `huinnyeoul.jpg` | 흰여울 문화마을 (영도) | https://commons.wikimedia.org/wiki/File:Huinnyeoul_culture_village,_Busan_on_October_25th,_2019.jpg | Choi2451 | CC0 | 2026-09-09 | 4032 × 3024 |
| `busan-night-panorama.jpg` | 부산 야경 — 광안대교와 마린시티 | https://commons.wikimedia.org/wiki/File:Gwangan_Bridge_seen_Marine_City_at_Night_01.jpg | Jeena Paradies | CC BY 2.0 | 2026-09-09 | 5889 × 3183 |

촬영자 이름은 **각 원본 페이지에서 실제로 읽은 문자열**이다. `bryan...` 은 점 세 개까지
그 사람의 Flickr 표시 이름 그대로다 — 줄임표가 아니다.

---

## 🔴 라이선스를 읽을 때 걸린 함정 하나

Commons 의 자동 메타데이터(`extmetadata`)는 이 다섯 중 **둘에 대해 라이선스를 틀리게**
알려줬다. `gwangan-bridge.jpg` 와 `busan-night-panorama.jpg` 를 **"Public domain"** 이라고
표시했는데, 실제 페이지 원문을 열어 보면 둘 다 그렇지 않다.

이유는 **`PD-structure`** (**"이 건축물 자체는 저작권이 없다" 는 별개의 표시**) 다.
한국은 다리·건물을 찍은 사진을 자유롭게 쓸 수 있어서 Commons 가 사진 페이지에 이
표시를 함께 달아 두는데, 자동 요약이 **건축물 쪽 표시를 사진의 라이선스로 착각**한다.

| 파일 | 자동 요약이 말한 것 | 페이지 원문의 실제 사진 라이선스 |
|---|---|---|
| `gwangan-bridge.jpg` | Public domain ❌ | **CC BY-SA 3.0** (`{{self\|cc-by-sa-3.0}}`) |
| `busan-night-panorama.jpg` | Public domain ❌ | **CC BY 2.0** (`{{cc-by-2.0}}`) |

**위 표에 적은 것은 페이지 원문 쪽이다.** 즉 이 두 장도 **촬영자 이름을 적어야 한다.**
자동 요약만 믿었으면 표기 없이 썼을 것이다.

---

## 손댄 것

`haeundae-beach.jpg` 만 **품질 85로 다시 인코딩**했다. 원본이 5,189,541 바이트라
"파일당 5MB 이하" 기준을 십진 기준(5,000,000 바이트)으로 보면 넘었다.
**픽셀은 하나도 자르지 않았다** — 크기는 6720 × 4480 그대로이고, 줄어든 것은
파일 용량뿐이다 (5,189,541 → 3,458,583 바이트). 나머지 넷은 내려받은 원본 그대로다.

## 검증

다섯 장 모두 아래를 통과했다 (종료 코드 0).

- 가로 2000px 이상
- 파일당 5,000,000 바이트 이하
- Pillow(**파이썬 이미지 처리 꾸러미**)로 열었을 때 `format == "JPEG"`

`busan-night-panorama.jpg` 는 위에 검정 그라데이션을 덮는 배경용이라 **어두울 것**이
요구사항이었다. 평균 밝기(0=검정, 255=흰색)를 재 보면 **25** 로 충분히 어둡다.
나머지 넷은 127 ~ 171 로 밝은 톤이다.
