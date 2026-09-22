package com.gabolle.backend.preference.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 읽는 쪽이 보는 취향 성분 하나 — {@code (차원, 코드)} 마다 정확히 하나.
 *
 * <h2>왜 저장 모양과 읽는 모양이 다른가</h2>
 *
 * {@link UserTasteWeight} 는 근거가 키의 일부라 한 성분이 <b>여러 줄</b>로 앉는다 (설문에서
 * 온 몫 한 줄, 행동에서 온 몫 한 줄). 나눠 적는 이유는 이벤트 하나만 들고 오는 소비자가
 * 행동 몫만 골라 고칠 수 있어야 하기 때문이다 (S15P21E201-1499 · S15P21E201-1500).
 *
 * <p>그런데 점수를 매기는 쪽에는 성분마다 숫자가 <b>하나</b>여야 한다. 그대로 넘기면 같은
 * 성분을 두 번 세기 때문이다. 그 합치기를 여기서 한다.
 *
 * <h2>🔴 합이지 평균이 아니다</h2>
 *
 * 나누기 전 {@code TasteVectorFoldService} 가 겹친 자리를 {@code clamp(설문 + 행동)} 으로
 * 적었다. 그래서 나눠 적고 여기서 다시 더하면 <b>정확히 같은 값</b>이 나온다 — 이 변경이
 * 점수를 안 바꾼다는 근거가 이것이다.
 *
 * <p>평균이 아닌 이유도 그때 정한 그대로다. 평균이면 행동이 아무 말도 안 할 때 설문의 세기가
 * 절반으로 깎인다. 자르는 것은 {@code ck_user_taste_weight_range} 때문이기도 하지만,
 * 「좋아한다」보다 더 좋아할 수는 없어서이기도 하다.
 *
 * @param weight -1(싫다) ~ +1(좋다)
 * @param evidence 합친 뒤의 출처. 설문과 행동이 둘 다 있으면 {@link TasteEvidence#BLENDED}
 * @param support 이 값을 뒷받침한 관측 수. 설문 몫은 0 이므로 사실상 행동 몫의 수다
 */
public record TasteWeightComponent(TasteDimension dimension, String code, double weight, TasteEvidence evidence,
		int support) {

	/**
	 * 저장된 줄들을 {@code (차원, 코드)} 마다 하나로 합친다.
	 *
	 * <p>🔴 <b>읽는 쪽은 반드시 이것을 거친다.</b> 저장 줄을 그대로 점수에 쓰면 설문 줄과
	 * 행동 줄이 각각 한 성분으로 세어져 같은 취향이 두 번 반영된다.
	 *
	 * <p>예전 방식으로 적힌 {@link TasteEvidence#BLENDED} 줄은 그 자체로 이미 합쳐진 값이라
	 * 그대로 한 성분이 된다. 그 줄의 설문 몫과 행동 몫은 되돌릴 수 없고, 되돌릴 필요도 없다 —
	 * 다음 배치가 그 사용자를 접을 때 새 판이 나뉘어 적힌다.
	 */
	public static List<TasteWeightComponent> merge(List<UserTasteWeight> rows) {
		if (rows == null || rows.isEmpty()) {
			return List.of();
		}

		// 넣은 순서를 지킨다. 점수에는 영향이 없지만(합과 개수만 쓴다) 근거를 남기는 칸의
		// 순서가 실행마다 흔들리면 「왜 이 점수인가」를 되짚을 때 diff 가 시끄럽다.
		Map<String, Accumulator> byComponent = new LinkedHashMap<>();
		for (UserTasteWeight row : rows) {
			if (tooThinToTrust(row)) {
				continue;
			}
			byComponent.computeIfAbsent(key(row.getDimension(), row.getCode()),
					(ignored) -> new Accumulator(row.getDimension(), row.getCode())).add(row);
		}

		List<TasteWeightComponent> merged = new ArrayList<>(byComponent.size());
		for (Accumulator accumulator : byComponent.values()) {
			merged.add(accumulator.toComponent());
		}
		return List.copyOf(merged);
	}

	/**
	 * 뒷받침이 모자란 <b>행동</b> 행인가 (S15P21E201-1500).
	 *
	 * <p>🔴 배치는 이런 행을 <b>만들지 않는다</b> — 전 이력을 다 세고 나서 모자란 것을 안
	 * 내보낸다. 소비자는 그럴 수가 없다. 관측이 하나씩 오므로 첫 번째에서 이미 행이 생기고
	 * 두 번째에 2 가 된다. 그래서 읽을 때 한 번 더 거른다.
	 *
	 * <p>설문 행은 안 거른다. 설문의 뒷받침은 0 이고, 그건 「관측이 모자라다」가 아니라
	 * <b>관측이라는 개념이 없다</b>는 뜻이다 — 사람이 직접 고른 값이다.
	 */
	private static boolean tooThinToTrust(UserTasteWeight row) {
		return row.getEvidence() == TasteEvidence.INTERACTION && row.getSupport() < TasteSignal.MIN_SUPPORT;
	}

	/**
	 * 차원과 코드를 한 키로.
	 *
	 * <p>구분자가 줄바꿈인 것은 코드에 들어올 수 있는 글자와 겹치지 않기 때문이다. 공백을 쓰면
	 * 코드에 공백이 들어온 날 서로 다른 성분이 같은 키가 된다.
	 */
	private static String key(TasteDimension dimension, String code) {
		return dimension.name() + '\n' + code;
	}

	/** 한 성분에 모이는 줄들을 더해 가는 자리. */
	private static final class Accumulator {

		private final TasteDimension dimension;

		private final String code;

		private double weight;

		private int support;

		private boolean hasSurvey;

		private boolean hasInteraction;

		private boolean hasBlended;

		private Accumulator(TasteDimension dimension, String code) {
			this.dimension = dimension;
			this.code = code;
		}

		private void add(UserTasteWeight row) {
			this.weight += row.getWeight();
			this.support += row.getSupport();
			switch (row.getEvidence()) {
				case SURVEY -> this.hasSurvey = true;
				case INTERACTION -> this.hasInteraction = true;
				case BLENDED -> this.hasBlended = true;
			}
		}

		private TasteWeightComponent toComponent() {
			return new TasteWeightComponent(this.dimension, this.code, clamp(this.weight), evidence(), this.support);
		}

		private TasteEvidence evidence() {
			if (this.hasBlended || (this.hasSurvey && this.hasInteraction)) {
				return TasteEvidence.BLENDED;
			}
			return this.hasInteraction ? TasteEvidence.INTERACTION : TasteEvidence.SURVEY;
		}

		private static double clamp(double value) {
			return Math.max(-1.0, Math.min(1.0, value));
		}

	}

}
