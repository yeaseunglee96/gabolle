package com.gabolle.backend.place.loader;

import java.util.List;

/**
 * 목록 근거 한 줄 — S15P21E201-826.
 *
 * <p>점수를 여기서 내지 않는다. 목록 하나가 얼마나 무거운지는 <b>그 목록에 몇 곳이
 * 올랐는가</b>에 따라 정해지므로 한 줄만 봐서는 알 수 없다 — {@link ListRarity} 가 낸다.
 *
 * @param storeId 상가업소번호. 장소 id 를 이 값에서 계산한다({@link SbizPlaceLoader#placeIdOf})
 * @param lists 오른 큐레이션 목록 이름들 — 블루리본 · 백년가게 · 택슐랭 · 블로그100 · 공개글448
 * @param mentions 공개글이 지목한 횟수. 그 칸은 「공개글448」에만 붙으므로 없을 수 있다
 */
public record TruthSignalRow(String storeId, List<String> lists, int mentions) {
}
