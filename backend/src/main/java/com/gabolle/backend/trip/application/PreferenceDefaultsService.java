package com.gabolle.backend.trip.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.gabolle.backend.trip.domain.PreferenceDimensions;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 계정 기본 취향의 읽기·쓰기와, 여행 답과 겹치는 규칙.
 *
 * <p>겹칠 때 여행 답이 항상 이긴다. 계정 기본값은 여행이 {@code UNKNOWN}(아예 안 물어봤다)인
 * 차원만 채운다 — {@code SKIPPED} 는 화면에서 보고 일부러 건너뛴 것이라 되살리지 않는다.
 * 반대로 {@link #carryOver} 는 계정에 이미 값이 있어도 덮어쓴다. 빈칸만 채우게 하면 계정
 * 기본값을 고치는 화면이 없는 동안 사용자가 첫 답에 영구히 갇힌다.
 */
@Service
public class PreferenceDefaultsService {

	/**
	 * 여행에서 답한 것을 계정 기본값으로 이어받을 차원. 기준은 "다음 여행에도 같은가" 하나다.
	 * {@code CATEGORY}·{@code ATMOSPHERE} 는 사람의 성향이 아니라 그 여행의 성격이라 뺐고,
	 * {@code SHADE_PREFERENCE} 는 계절에 뒤집혀 이어받으면 겨울에 그늘길을 추천하게 된다.
	 * {@code SPEND_PROFILE} 은 {@code SpendProfileService} 가 이미 계정에 저장한다. 알레르기·
	 * 식단은 취향이 아니라 제약이고, 화면이 저장하지 않겠다고 약속해 두었다.
	 */
	private static final Set<String> CARRY_OVER = Set.of(
			"LOCALITY", "QUIETNESS", "TOURIST_PREFERENCE", "FOOD_PREFERENCE", "SLOPE_PREFERENCE");

	private final TripRepository repository;

	private final Clock clock;

	public PreferenceDefaultsService(TripRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** 이 계정의 기본 취향 최신 판. 한 번도 저장하지 않았으면 비어 있다. */
	public Optional<PreferenceSnapshot> find(String userId) {
		return this.repository.findUserDefaults(userId);
	}

	/**
	 * 계정 기본 취향을 새 판으로 바꾼다. 부분 갱신이 아니다 — 넘긴 목록이 그 판의 전부이고,
	 * 빠진 차원은 "값이 없다" 가 된다.
	 */
	public PreferenceSnapshot replace(String userId, List<PreferenceSnapshot.PreferenceAnswer> answers) {
		Instant now = Instant.now(this.clock);
		return this.repository.saveUserDefaults(userId, (answers == null) ? List.of() : answers, now);
	}

	/**
	 * 여행 답 위에 계정 기본값을 겹친다. 계정 기본값 쪽에서는 {@code SELECTED} 만 쓴다 —
	 * 나머지는 값이 없다는 뜻이라 채울 것이 없다.
	 *
	 * @return 겹친 결과. 계정 기본값이 없으면 {@code tripAnswers} 그대로
	 */
	public List<PreferenceSnapshot.PreferenceAnswer> overlayDefaults(String userId,
			List<PreferenceSnapshot.PreferenceAnswer> tripAnswers) {

		List<PreferenceSnapshot.PreferenceAnswer> answers =
				(tripAnswers == null) ? List.of() : tripAnswers;
		PreferenceSnapshot defaults = this.repository.findUserDefaults(userId).orElse(null);
		if (defaults == null || defaults.answers().isEmpty()) {
			return answers;
		}

		// 차원 이름을 대문자로 맞춰 견준다. 화면의 camelCase 와 DB 어휘의 대문자가 섞여 있어서,
		// 대소문자를 그대로 비교하면 같은 차원이 둘로 보여 기본값이 여행 답을 덮어쓴다.
		Map<String, PreferenceSnapshot.PreferenceAnswer> merged = new LinkedHashMap<>();
		for (PreferenceSnapshot.PreferenceAnswer answer : answers) {
			merged.put(key(answer.dimension()), answer);
		}
		for (PreferenceSnapshot.PreferenceAnswer fallback : defaults.answers()) {
			if (fallback.status() != PreferenceSnapshot.AnswerStatus.SELECTED) {
				continue;
			}
			PreferenceSnapshot.PreferenceAnswer existing = merged.get(key(fallback.dimension()));
			if (existing == null || existing.status() == PreferenceSnapshot.AnswerStatus.UNKNOWN) {
				merged.put(key(fallback.dimension()), fallback);
			}
			// SELECTED · SKIPPED 는 그대로 둔다.
		}
		return List.copyOf(merged.values());
	}

	/**
	 * 이 여행에서 고른 답을 계정 기본값으로 이어받는다 — {@link #overlayDefaults} 의 반대 방향.
	 * {@code SELECTED} 이면서 {@link #CARRY_OVER} 에 있는 차원만 옮기고, 계정에 이미 값이 있어도
	 * 덮어쓴다. 값이 같으면 저장하지 않는다 — 화면이 채워진 값을 그대로 돌려보내므로, 매번 판을
	 * 새로 쓰면 {@code created_at} 만 다른 판이 쌓여 "언제 정한 취향인가" 를 못 본다.
	 *
	 * @param tripAnswers 겹치기 전의 것이어야 한다 — 겹친 뒤의 목록에는 계정 기본값이 섞여 있어
	 *        사용자가 답하지 않은 차원까지 "고른 것" 으로 보인다
	 * @return 실제로 바뀐 차원 이름. 바뀐 것이 없으면 빈 목록이고 저장도 하지 않는다
	 */
	public List<String> carryOver(String userId, List<PreferenceSnapshot.PreferenceAnswer> tripAnswers) {
		if (userId == null || tripAnswers == null || tripAnswers.isEmpty()) {
			return List.of();
		}
		Map<String, PreferenceSnapshot.PreferenceAnswer> kept = new LinkedHashMap<>();
		this.repository.findUserDefaults(userId).ifPresent(defaults -> {
			for (PreferenceSnapshot.PreferenceAnswer answer : defaults.answers()) {
				kept.put(key(answer.dimension()), answer);
			}
		});

		List<String> changed = new ArrayList<>();
		for (PreferenceSnapshot.PreferenceAnswer answer : tripAnswers) {
			if (answer.status() != PreferenceSnapshot.AnswerStatus.SELECTED) {
				continue;
			}
			String dimension = key(answer.dimension());
			if (!CARRY_OVER.contains(dimension)) {
				continue;
			}
			PreferenceSnapshot.PreferenceAnswer existing = kept.get(dimension);
			if (existing != null && existing.status() == PreferenceSnapshot.AnswerStatus.SELECTED
					&& Objects.equals(existing.valueJson(), answer.valueJson())) {
				continue; // 같은 값이다. 판을 새로 쓸 이유가 없다
			}
			kept.put(dimension, answer);
			changed.add(dimension);
		}
		if (changed.isEmpty()) {
			return List.of();
		}
		replace(userId, List.copyOf(kept.values()));
		return List.copyOf(changed);
	}

	/**
	 * 계정 기본 취향 중 보낸 차원만 바꾼다 — 온보딩과 마이페이지가 쓴다. {@link #carryOver} 와
	 * 하는 일은 같지만 {@code UNKNOWN} 의 뜻이 다르다. 여행을 만들 때의 {@code UNKNOWN} 은
	 * "화면이 안 물어봤다" 라 아무것도 안 하고, 여기 오는 {@code UNKNOWN} 은 "이 취향을 잊어
	 * 달라" 라 그 차원을 지운다.
	 *
	 * @param answers 바꿀 차원만. 앱 이름({@code locality})과 어휘({@code LOCALITY}) 둘 다 받는다
	 * @return 실제로 바뀐 차원 이름. 바뀐 것이 없으면 빈 목록이고 저장도 하지 않는다
	 * @throws IllegalArgumentException 계정 기본값으로 둘 수 없는 차원이 섞여 있을 때. 조용히
	 *         버리지 않는다 — 버리면 화면은 저장된 줄 알고 다음에 빈칸을 본다
	 */
	public List<String> putTaste(String userId, List<PreferenceSnapshot.PreferenceAnswer> answers) {
		if (userId == null || answers == null || answers.isEmpty()) {
			return List.of();
		}
		Map<String, PreferenceSnapshot.PreferenceAnswer> kept = new LinkedHashMap<>();
		this.repository.findUserDefaults(userId).ifPresent(defaults -> {
			for (PreferenceSnapshot.PreferenceAnswer answer : defaults.answers()) {
				kept.put(key(answer.dimension()), answer);
			}
		});

		List<String> changed = new ArrayList<>();
		for (PreferenceSnapshot.PreferenceAnswer answer : answers) {
			String dimension = PreferenceDimensions.normalize(answer.dimension());
			if (!CARRY_OVER.contains(dimension)) {
				throw new IllegalArgumentException(
						"계정 기본값으로 둘 수 없는 차원입니다: " + answer.dimension()
						+ " (둘 수 있는 것: " + String.join(", ", CARRY_OVER) + ")");
			}
			PreferenceSnapshot.PreferenceAnswer existing = kept.get(dimension);
			switch (answer.status()) {
				case SELECTED -> {
					if (existing != null && existing.status() == PreferenceSnapshot.AnswerStatus.SELECTED
							&& Objects.equals(existing.valueJson(), answer.valueJson())) {
						continue; // 같은 값이다. 판을 새로 쓸 이유가 없다
					}
					kept.put(dimension, new PreferenceSnapshot.PreferenceAnswer(
							dimension, answer.valueJson(), PreferenceSnapshot.AnswerStatus.SELECTED));
					changed.add(dimension);
				}
				case UNKNOWN -> {
					if (existing == null) {
						continue; // 없는 것을 지울 수는 없다
					}
					kept.remove(dimension);
					changed.add(dimension);
				}
				case SKIPPED -> {
					// 물어봤는데 안 답했다. 계정을 안 건드린다.
				}
			}
		}
		if (changed.isEmpty()) {
			return List.of();
		}
		replace(userId, List.copyOf(kept.values()));
		return List.copyOf(changed);
	}

	/**
	 * 계정 기본 취향 중 {@link #CARRY_OVER} 다섯만 추린다. {@link #find} 와 달리
	 * {@code SPEND_PROFILE} 을 뺀다 — 그 차원은 별도 엔드포인트가 맡고 있어, 두 화면이 같은
	 * 값을 각자 그리면 한쪽만 고쳤을 때 어긋난다.
	 */
	public List<PreferenceSnapshot.PreferenceAnswer> findTaste(String userId) {
		return this.repository.findUserDefaults(userId)
				.map(PreferenceSnapshot::answers)
				.orElseGet(List::of)
				.stream()
				.filter(a -> CARRY_OVER.contains(key(a.dimension())))
				.toList();
	}

	private static String key(String dimension) {
		return dimension.toUpperCase(Locale.ROOT);
	}
}
