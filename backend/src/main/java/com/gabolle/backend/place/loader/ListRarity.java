package com.gabolle.backend.place.loader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 목록마다 무게를 희소성에서 뽑는다 — S15P21E201-826.
 *
 * <h2>왜 목록을 같은 무게로 세지 않나</h2>
 * 처음에는 오른 목록 수를 그대로 셌다. 그러면 <b>백년가게 등재와 공개글 언급이 같은 값</b>이
 * 된다. 백년가게는 22곳뿐이고 공개글은 272곳이라, 두 사실이 말해 주는 것의 양이 다르다.
 *
 * <p>장효준이 제안한 방식을 쓴다(2026-09-11). 검색에서 오래 쓰인 것과 같은 셈이다 —
 * <b>흔한 목록에 실리면 정보가 적고 드문 목록에 실리면 정보가 많다.</b>
 *
 * <pre>
 * 무게 = ln(전체 / 그 목록의 등재 수)
 * </pre>
 *
 * <p>가장 무거운 목록이 1.0 이 되도록 나눠서 0~1 로 만든다. <b>사람이 숫자를 고르지
 * 않는다</b>는 것이 이 방식의 요점이다 — 무게가 자료에서 나온다. 실제 417곳으로 계산하면
 * 백년가게 1.00 · 택슐랭 0.74 · 블루리본 0.70 · 블로그100 0.69 · 공개글448 0.15 이 된다.
 *
 * <h2>여러 목록에 오르면 더한다</h2>
 * 오른 목록이 많을수록 가리키는 곳이 많다는 뜻이므로 무게를 더하고, 1.0 에서 자른다.
 *
 * <h2>글 지목 수는 점수에 안 쓴다</h2>
 * 공개글이 몇 번 지목했는지는 값에 함께 남기지만 점수에는 안 넣는다. 넣으려면 "글 몇 편이
 * 목록 하나만큼인가" 를 사람이 골라야 하는데, 그 숫자를 없애는 것이 이 방식의 목적이다.
 */
public final class ListRarity {

    private final Map<String, Double> weights;

    private ListRarity(Map<String, Double> weights) {
        this.weights = weights;
    }

    /** 넘긴 줄들만으로 무게를 만든다. 자료가 바뀌면 무게도 따라 바뀐다. */
    public static ListRarity from(List<TruthSignalRow> rows) {
        Map<String, Integer> appearances = new HashMap<>();
        for (TruthSignalRow row : rows) {
            for (String list : row.lists()) {
                appearances.merge(list, 1, Integer::sum);
            }
        }
        int total = rows.size();
        Map<String, Double> raw = new HashMap<>();
        double max = 0.0;
        for (Map.Entry<String, Integer> entry : appearances.entrySet()) {
            // 등재 수가 전체와 같으면 ln(1)=0 이다 — 모두가 오른 목록은 아무것도 안 가른다.
            double idf = Math.log((double) total / entry.getValue());
            raw.put(entry.getKey(), idf);
            max = Math.max(max, idf);
        }
        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> entry : raw.entrySet()) {
            // 무게가 전부 0 인 자료(목록이 하나뿐이고 모두가 거기 올랐다)에서는 0 으로 둔다.
            normalized.put(entry.getKey(), max == 0.0 ? 0.0 : entry.getValue() / max);
        }
        return new ListRarity(Map.copyOf(normalized));
    }

    /** 모르는 목록은 0 이다. 지어낸 무게를 주지 않는다. */
    public double weightOf(String list) {
        return this.weights.getOrDefault(list, 0.0);
    }

    /** 0~1 점수. 오른 목록들의 무게를 더하고 1.0 에서 자른다. */
    public double scoreOf(TruthSignalRow row) {
        double sum = 0.0;
        for (String list : row.lists()) {
            sum += weightOf(list);
        }
        return Math.min(1.0, sum);
    }
}
