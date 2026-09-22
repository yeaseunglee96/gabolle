package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gabolle.backend.trip.domain.Trip;

/**
 * 총예산 맞춤. 사용자가 답한 예산과 장소의 가격대({@code PRICE_LEVEL})를 견주어 점수를 올리거나
 * 내린다.
 *
 * <p><b>왜 생겼나.</b> 온보딩에서 총예산은 <b>「필수」</b>다. 건너뛸 수 없고 최소 1만원·1만원
 * 단위 검증까지 붙어 있다. 그런데 추천과 일정에서 {@code budgetKrw} 를 참조하는 곳이 <b>0건</b>
 * 이었다 — 반드시 답하게 해 놓고 아무 일도 안 했다.
 *
 * <p><b>후보에서 빼지 않는다.</b> 점수만 움직인다. 비싼 곳도 목록에 남아서 사용자가 보고 고를 수
 * 있다. {@link SeedBoost} 와 같은 자리에서 같은 방식으로 돈다.
 *
 * <p>🔴 <b>「모른다」를 「비싸다」로 치지 않는다.</b> 가격대가 붙은 곳은 아직 일부라, 모름을
 * 벌주면 조사가 안 된 곳이 「비싸서」가 아니라 「모르니까」 통째로 뒤로 밀린다. 그리고 그 사실은
 * 화면 어디에도 안 보인다.
 */
public final class BudgetFit {

	/** 예산 안에 드는 곳에 더한다. */
	public static final double MATCH_BONUS = 0.10;

	/** 예산을 넘는 곳에서 뺀다. 빼는 쪽을 작게 둔 것은 후보를 지우는 것이 아니라 뒤로 미는 것이기 때문이다. */
	public static final double OVER_PENALTY = 0.05;

	public static final String REASON_WITHIN_BUDGET = "WITHIN_BUDGET";

	public static final String REASON_OVER_BUDGET = "OVER_BUDGET";

	/** 가격대 표식의 갈래 이름. 적재기가 이 이름으로 넣는다({@code PlaceFeatureNdjsonReader.readPriceBands}). */
	public static final String FEATURE_TYPE = "PRICE_LEVEL";

	/**
	 * 싼 것부터 비싼 것까지. 비교는 이 차례의 자리번호로 한다 — 등급을 숫자로 바꿔 저장하지
	 * 않는 것이 적재기의 결정이고({@code "등급을 숫자로 바꾸지 않는다"}), 그 결정을 여기서
	 * 뒤집지 않는다. 접는 것은 쓰는 쪽에서 해도 되지만 펴는 것은 못 한다.
	 */
	private static final List<String> BANDS = List.of("LOW", "MID", "MID_HIGH", "HIGH");

	/**
	 * 1인 1끼 예산의 경계(원). 부산 기준으로 사람이 정한 값이다 —
	 * {@code LOW} 돼지국밥·백반·분식 · {@code MID} 일반 식당·카페 ·
	 * {@code MID_HIGH} 횟집·고깃집 · {@code HIGH} 오마카세·호텔.
	 * <p>
	 * 🔴 <b>실측이 아니라 정한 값이다.</b> 실제 결제 자료를 보게 되면 여기를 고친다.
	 */
	private static final int[] BAND_CEILINGS_KRW = { 10_000, 20_000, 40_000 };

	/**
	 * 하루 끼니 수. 일정 쪽({@code ItineraryDraftService.mealsPerDay})이 활동 시간대로 계산하는
	 * 값의 기본값과 같다. 그 계산을 여기서 다시 하지 않는 것은, 두 벌이 되면 한쪽만 고쳐졌을 때
	 * 예산 판정과 실제 끼니 수가 어긋나고 그 어긋남이 어느 화면에도 안 보이기 때문이다.
	 */
	private static final int MEALS_PER_DAY = 2;

	private BudgetFit() {
	}

	/**
	 * 이 여행의 1인 1끼 예산이 어느 등급인가.
	 *
	 * <p>🔴 <b>이 값은 실제보다 넉넉하게 잡힌다.</b> 여행 예산 전부를 끼니로 나눈 값이라
	 * 교통비·입장료가 안 빠져 있다. 그 비중을 뺄 근거가 아직 없어서 일부러 안 뺀다 — 지어낸
	 * 계수를 하나 더 넣는 것보다 넉넉하다는 것을 적어 두는 쪽이 낫다.
	 *
	 * @return 등급. 예산을 안 받았거나 계산이 안 되면 {@code null} — 그때는 아무것도 하지 않는다
	 */
	public static String targetBand(Trip trip) {
		if (trip == null || trip.budgetKrw() == null || trip.budgetKrw() <= 0) {
			return null;
		}
		int people = Math.max(1, trip.partySize());
		int days = Math.max(1, trip.days());
		long perMeal = (long) trip.budgetKrw() / people / days / MEALS_PER_DAY;
		for (int i = 0; i < BAND_CEILINGS_KRW.length; i++) {
			if (perMeal <= BAND_CEILINGS_KRW[i]) {
				return BANDS.get(i);
			}
		}
		return BANDS.get(BANDS.size() - 1);
	}

	/**
	 * 후보의 점수를 예산에 맞춰 움직인다.
	 *
	 * @param bandByPlace 후보마다의 가격대. 모르는 곳은 아예 빠져 있거나 {@code null} 이다
	 * @param targetBand {@link #targetBand(Trip)} 의 값. {@code null} 이면 입력을 그대로 돌려준다
	 */
	public static List<EngineCandidate> apply(List<EngineCandidate> candidates,
			Map<UUID, String> bandByPlace, String targetBand) {

		if (targetBand == null || bandByPlace == null || bandByPlace.isEmpty()) {
			return candidates;
		}
		int target = BANDS.indexOf(targetBand);
		if (target < 0) {
			return candidates;
		}

		List<EngineCandidate> adjusted = new ArrayList<>(candidates.size());
		for (EngineCandidate candidate : candidates) {
			int band = BANDS.indexOf(String.valueOf(bandByPlace.get(candidate.placeId())));
			if (band < 0) {
				// 가격대를 모르는 곳이다. 아무것도 더하지도 빼지도 않는다.
				adjusted.add(candidate);
				continue;
			}
			adjusted.add(move(candidate, band <= target));
		}
		return adjusted;
	}

	private static EngineCandidate move(EngineCandidate c, boolean withinBudget) {
		double delta = withinBudget ? MATCH_BONUS : -OVER_PENALTY;
		// 점수가 없는 후보는 0 으로 치지 않는다 — 없는 것과 낮은 것은 다르다.
		Double score = (c.preRankScore() == null) ? null : c.preRankScore() + delta;

		Map<String, Object> components = new LinkedHashMap<>(c.scoreComponents());
		components.put("budget", Map.of("delta", delta, "withinBudget", withinBudget));

		List<String> reasons = new ArrayList<>(c.reasonCodes());
		reasons.add(withinBudget ? REASON_WITHIN_BUDGET : REASON_OVER_BUDGET);

		return new EngineCandidate(c.placeId(), c.candidateSource(), c.constraintVerdict(), c.violations(),
				c.unknownFacts(), c.constraintConfidence(), c.featureValues(), components, score, reasons,
				c.warningCodes());
	}
}
