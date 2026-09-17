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
          "featureValues": { "…": 0 },      // 채점기가 남긴 그대로
          "tags": {                         // 🔴 태그 원본 — 아래 참고
            "CUISINE_TAG": ["MILMYEON"],
            "CATEGORY_TAG": ["FOOD"]
          }
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

## 🔴 `tags` 를 왜 따로 싣나

채점기가 남기는 `featureValues` 에는 겹침 **비율**만 있다(`interestTagOverlap` 0.5 처럼).
그 값으로는 **어떤 낱말이 겹쳤는지**를 알 수 없어서, 02단계가 심어 둔 취향과 맞춰 볼 수가 없다.
그래서 장소의 태그 원본을 함께 싣는다.

## 🔴 점수형 피처는 기대하지 않는다

`quietnessScore` · `localityScore` 같은 점수형은 **배포 서버에서 0곳**이다
(2026-09-17 실측, `lib/axes.mjs`). 순위표에 그 칸이 비어 오는 것이 지금은 정상이고,
시뮬레이터도 그 축에는 취향을 안 심는다.
