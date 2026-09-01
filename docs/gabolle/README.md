# GABOLLE 팀 공유 문서

이 폴더의 v1.1 문서 6종이 GABOLLE(가볼래)의 현재 제품·설계 기준입니다.

| 문서 | 사용하는 사람 | 역할 |
|---|---|---|
| `GABOLLE_통합_서비스_기획서_v1.1.docx` | 전원 | 목표, MVP 범위, 기술·팀 책임 |
| `GABOLLE_요구사항_명세서_v1.1.docx` | 기획·BE·FE·APP·DATA·REC/AI·QA | 기능·데이터·비기능 요구사항과 ID |
| `GABOLLE_API_명세서_v1.1.docx` | BE·FE·APP·DATA·REC/AI | 공개/내부 API, Job, 이벤트, 오류 계약 |
| `GABOLLE_도메인_온톨로지_명세서_v1.1.docx` | DATA·REC/AI·BE | 개념, 관계, PASS/FAIL/UNKNOWN 판정 |
| `GABOLLE_화면흐름_및_정보구조_명세서_v1.1.docx` | FE·APP·기획 | 화면 ID, 전환, API·이벤트 연결 |
| `GABOLLE_사용자_시나리오_및_인수기준_v1.1.docx` | 전원·QA | 사용자 흐름과 완료 판정 기준 |

## 확정 기준

- 서비스명: 가볼래(GABOLLE)
- DB: PostgreSQL
- MVP 로그인: Google, Naver, Kakao
- 행동 개인화: 사용자가 OFF 가능하며 명시 취향·Editor's Pick 추천은 유지
- 위치: 사용자가 여행 실행을 시작한 동안 앱 foreground에서만 수집
- 그룹: 생성자는 OWNER, 초대 구성원은 모두 EDITOR
- 책임: Spring 후보 생성·공개 계약, 온톨로지 제약 판정, Python 추천 랭킹·동선 최적화

`LOCAL_ROUTE_*` 문서는 과거 설계와 검토 이력을 위한 참고 자료입니다. 충돌하면 이 폴더의 v1.1 문서를 우선합니다.
