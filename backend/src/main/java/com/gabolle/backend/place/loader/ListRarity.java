package com.gabolle.backend.place.loader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 목록마다 무게를 희소성에서 뽑는다. 오른 목록 수를 그대로 세면 드문 목록 등재와 흔한 목록
 * 언급이 같은 값이 되는데, 두 사실이 말해 주는 양은 다르다.
 *
 * <pre>
 * 무게 = ln(전체 / 그 목록의 등재 수)
 * </pre>
 *
 * <p>가장 무거운 목록이 1.0 이 되도록 나눠 0~1 로 만든다. 요점은 사람이 숫자를 고르지 않는
 * 것이다 — 무게가 자료에서 나온다. 여러 목록에 오르면 무게를 더하고 1.0 에서 자른다.
 *
 * <p>공개글 지목 수는 값에 남기지만 점수에는 안 넣는다. 넣으려면 "글 몇 편이 목록 하나만큼인가"
 * 를 사람이 골라야 하는데, 그 숫자를 없애는 것이 이 방식의 목적이다.
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
