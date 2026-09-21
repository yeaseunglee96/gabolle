package com.gabolle.backend.preference.domain;

/**
 * 취향 벡터의 숫자 하나가 어디서 나왔는가. 안 남기면 사람이 직접 고른 것과 우리가 추측한
 * 것이 같아 보이는데, 직접 고른 것을 추측으로 덮어쓰면 안 되고 추천 이유를 설명하는 말도
 * 달라진다.
 */
public enum TasteEvidence {

	/** 설문에서 사람이 직접 골랐다. */
	SURVEY,

	/** 앱에서의 행동만으로 추측했다. 사람이 그 차원을 답한 적이 없다. */
	INTERACTION,

	/** 설문 답을 행동으로 보정했다. */
	BLENDED
}
