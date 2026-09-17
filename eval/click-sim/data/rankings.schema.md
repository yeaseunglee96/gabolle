# `data/rankings.json` — 순위표 계약

**이 파일은 여기서 만들지 않는다.** `backend` 의 하네스가 실제 배포되는
`BaselineCandidateScorer`(Java)를 직접 불러 찍어 준다.

> 🔴 채점 로직을 여기서 다시 구현하지 않는다. `eval/food-ranking/lib/baseline-scorer.mjs`
> 가 그 사본이고, 그 파일 머리말이 위험을 직접 적어 뒀다 —
> *"옮겨 적다가 틀리면 「배포될 것을 쟀다」 는 말 자체가 거짓이 된다."*
> 사본을 하나 더 만드는 대신, 진짜를 부른다.

`BaselineCandidateScorer` 는 **DB 를 모르는 순수 함수에 가깝게** 만들어져 있다
(클래스 javadoc). 그래서 스프링 컨텍스트도 DB 도 없이 직접 부를 수 있고,
그것이 사본 없이 진짜를 재는 길이다.

## 모양

```jsonc
{
  "datasetVersion": "synthetic-v1",   // 🔴 반드시. P0 명세가 기본값 대체를 금지한다
  "scorerVersion": "...",             // model_version 과 같은 값
  "weights": { "distance": 0.30, "interest": 0.20, "...": 0 },
  "generatedAt": "2026-09-17T…Z",
  "rankings": [
    {
      "userId": "sim-000-high",       // 01-users.mjs 가 만든 id
      "requestId": "…",               // UUID. 분석 단위 (request_id, place_id) 의 앞쪽
      "candidates": [
        {
          "placeId": "…",
          "originalRank": 1,          // 채점 직후 순위
          "finalRank": 1,             // 다양성 재정렬 뒤 순위 ← 노출은 이쪽에 건다
          "preRankScore": 0.734,
          "featureValues": { "quietnessScore": 0.8, "…": 0 }
        }
      ]
    }
  ]
}
```

## 🔴 두 순위를 **다** 싣는다

`originalRank` 와 `finalRank` 는 이미 `recommendation_candidate` 에 둘 다 저장된다.
노출은 `finalRank` 에 걸지만(사용자가 보는 것이 그쪽이다), 나중에 위치 편향을
보정하려면 **둘의 차이**가 필요하다. 하나만 실으면 그때 다시 만들어야 한다.

## 심은 취향을 채점기에 어떻게 넣나

가상 사용자의 취향은 `PreferenceSnapshot` 모양으로 만들어 넣는다 — 실제 사용자의
설문 답이 지나는 길과 같아야 "배포될 것을 쟀다" 가 참이 된다.

점수형 다섯은 `{"score": 0.85}`, 태그형은 맨 배열 `["SEA_BEACH","FOOD"]` 이다
(`PreferenceJson` 참고 — 앱이 실제로 보내는 모양).
