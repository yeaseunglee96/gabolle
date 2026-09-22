package com.gabolle.backend.place.loader;

import java.util.List;

/**
 * 목록 근거 한 줄. 점수는 여기서 내지 않는다 — 목록 하나의 무게는 그 목록에 몇 곳이
 * 올랐는가로 정해져 한 줄만 봐서는 알 수 없고, {@link ListRarity} 가 낸다.
 * {@code mentions} 는 공개글 목록에만 붙는 칸이라 없을 수 있다.
 */
public record TruthSignalRow(String storeId, List<String> lists, int mentions) {
}
