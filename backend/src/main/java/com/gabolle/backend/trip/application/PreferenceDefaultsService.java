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

import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 계정 기본 취향 — 읽기·쓰기와 <b>여행 답과 겹치는 규칙</b> (S15P21E201-547).
 *
 * <h2>왜 이 서비스가 필요했나</h2>
 *
 * <p>{@code PersonalizationScope.USER} 가 {@code main/java} 어디에서도 쓰이지 않고 있었다.
 * 쓰이는 곳은 {@code TripCreationService}·{@code JpaTripRepository} 의 {@code TRIP} 뿐이고
 * 스냅샷 조회는 전부 여행 키 기준이었다. 즉 <b>계정 기본 취향이 저장되지도 읽히지도
 * 않았다.</b> 스키마는 이미 그것을 받을 준비가 되어 있었다({@code trip_id} 가 USER 일 때
 * NULL, {@code uq_preference_snapshot_user} 부분 유니크 색인) — 코드가 그 경로를 안 썼다.
 *
 * <p>그래서 같은 사람이 두 번째 여행을 만들면 알레르기부터 다시 답해야 했다.
 *
 * <h2>🔴 겹치는 규칙 — 무엇이 무엇을 이기나</h2>
 *
 * <p>여행에서 답한 값이 <b>항상</b> 이긴다. 계정 기본값은 그 여행이 <b>답하지 않은</b>
 * 차원만 채운다. 반대 방향은 절대 없다 — 여행에서 고친 값이 계정 기본값을 덮어쓰지
 * 않는다({@code PersonalizationScope} javadoc · 명세 2.2). 그것이 이 티켓의 요구다.
 *
 * <table border="1">
 * <caption>차원 하나를 두고 여행 답과 계정 기본값이 만났을 때</caption>
 * <tr><th>여행 답</th><th>결과</th><th>왜</th></tr>
 * <tr><td>{@code SELECTED}</td><td><b>여행 답</b></td>
 *     <td>이 여행에 대해 사용자가 직접 말했다</td></tr>
 * <tr><td>{@code SKIPPED}</td><td><b>여행 답(건너뜀 유지)</b></td>
 *     <td>🔴 화면에서 보고 <b>일부러</b> 건너뛴 것이다. 기본값으로 되살리면
 *         "이번엔 이 조건 빼고" 가 무시된다 — 사용자는 그 이유를 알 수 없다</td></tr>
 * <tr><td>{@code UNKNOWN}</td><td><b>계정 기본값</b></td>
 *     <td>아예 안 물어봤다는 뜻이라 사용자의 의사가 없다. 여기를 채우는 것이
 *         "20문항을 다시 앉혀 두지 않는다" 의 실체다</td></tr>
 * <tr><td>(차원 자체가 없다)</td><td><b>계정 기본값</b></td>
 *     <td>{@code UNKNOWN} 과 같다</td></tr>
 * </table>
 *
 * <p>🔴 {@code SKIPPED} 와 {@code UNKNOWN} 을 가르는 것이 이 표의 핵심이고, 그 둘을 굳이
 * 따로 저장해 둔 이유가 여기서 처음 쓰인다(-542 2.1 이 셋을 구분하라고 한 것). 뭉개서
 * 하나로 뒀다면 이 규칙을 쓸 수 없었다.
 *
 * <h2>🔴 이 서비스는 지금 아무도 부르지 않는다 — 쓰기 쪽이 비어 있다</h2>
 *
 * <p>{@link #replace} 를 부르는 HTTP 경로를 <b>일부러 만들지 않았다.</b> 이 저장소는
 * "경로와 응답 코드를 발명하지 않는다"({@code TripController} javadoc)를 지키고 있고,
 * 계정 기본 취향을 저장하는 경로는 API 명세(GB-API-001)에 아직 없다. 지어내면 화면이
 * 그것에 맞춰 만들어진 뒤에 명세가 다른 이름을 정할 수 있다.
 *
 * <p>그래서 지금은 <b>읽기와 겹치기만 동작한다</b> — 계정 기본값이 있으면
 * {@link TripCreationService} 가 그것을 여행 스냅샷에 채운다. 값이 없으면 지금까지와
 * 똑같이 동작한다. 경로가 정해지면 컨트롤러 하나가 {@link #replace} 를 부르면 된다.
 *
 * <h2>🔴 2026-09-15 — 여행에서 고른 답을 계정에 이어받는다 (S15P21E201-639)</h2>
 *
 * <p>위 문단이 <i>"쓰기 쪽이 비어 있다"</i> 고 적어 둔 뒤로 실제로 무슨 일이 일어났는지를
 * 재 보니, <b>계정 기본값을 가진 사람이 소비 성향 한 차원뿐</b>이었다(2026-09-15 운영 DB:
 * USER 스냅샷 9건, 전부 {@code SPEND_PROFILE}). 나머지 여덟 차원은 저장되는 곳이 없어서
 * 겹치기가 채울 것이 없었고, 그래서 <b>같은 사람이 두 번째 여행을 만들어도 처음부터 다시
 * 답해야 했다.</b> 이 클래스 javadoc 이 애초에 없애려던 그 상황이 그대로 남아 있었다.
 *
 * <p>그래서 {@link #carryOver} 를 더한다. 여행에서 <b>실제로 고른</b> 답을 계정 기본값으로
 * 옮긴다.
 *
 * <h3>🔴 "빈칸만 채운다" 로 먼저 만들었다가 되돌렸다 — 갇히기 때문이다</h3>
 *
 * <p>처음에는 명세 2.2(<i>"여행에서 고친 값이 계정 기본값을 덮어쓰지 않는다"</i>)를 그대로
 * 지키려고 <b>계정에 그 차원이 비어 있을 때만</b> 넣었다. 그런데 그러면 이렇게 된다.
 *
 * <pre>
 *   첫 여행    조용한 곳 "4"  →  계정에 저장됨
 *   두 번째    화면에 4가 채워짐 → 사용자가 "1" 로 고침
 *              그 1 은 그 여행에만 적용. 계정은 여전히 4
 *   세 번째    또 4 가 채워짐
 *   ...        영원히 4
 * </pre>
 *
 * <p>🔴 <b>계정 기본값을 고치는 화면이 없다</b>(2026-09-15 기준, 소비 성향 하나만 고칠 수
 * 있다). 그래서 사용자는 <b>첫 답에 영구히 갇힌다.</b> 다리를 다쳤을 때 "경사 피하기" 로
 * 답하면 다 나은 뒤에도 평생 언덕을 피해 다니는 추천을 받고, 되돌릴 방법이 없다.
 *
 * <p><b>그것은 매번 다시 묻는 것보다 나쁘다.</b> 귀찮은 쪽은 최소한 지금 상태를 반영한다.
 *
 * <h3>🔴 명세 2.2 의 전제가 바뀌었다</h3>
 *
 * <p>2.2 가 막으려던 것은 <i>"사용자가 모르게 프로필이 바뀌는 일"</i> 이고, 그것이 옳았던
 * 까닭은 그때 <b>계정 기본값이 화면에 안 보였기</b> 때문이다 — 채워지지 않았으니 볼 수가
 * 없었다.
 *
 * <p>이 티켓이 채우기를 만들면서 <b>그 값이 화면에 보이게 됐다.</b> 두 번째 여행부터
 * 사용자는 채워진 값을 보고 그대로 두거나 고친다. <b>보고 고친 것이 반영되는 것은
 * "모르게 바뀌는 일" 이 아니다.</b> 오히려 반영이 안 되는 쪽이 놀라운 일이다.
 *
 * <p>그래서 채우기와 고치기를 <b>같이</b> 둔다. 채우기만 넣고 고치기를 막으면 가둔다.
 *
 * <h3>안 건드리는 두 가지는 그대로다</h3>
 *
 * <ul>
 * <li>{@code SKIPPED} — "이번 여행만 이 조건 빼고" 다. 계정을 안 건드린다.
 *     {@link #overlayDefaults} 가 건너뜀을 되살리지 않는 것과 짝이다</li>
 * <li>{@code UNKNOWN} — 아예 안 물어봤으니 사용자의 의사가 없다</li>
 * </ul>
 *
 * <h3>🔴 다섯 차원만 이어받는다</h3>
 *
 * <p>{@link #CARRY_OVER} 를 보라. 기준은 하나다 — <b>다음 여행에도 같은가.</b>
 * 경사를 못 오르는 몸은 다음 달에도 같지만, 이번에 바다를 보고 싶은 마음은 다음에 다르다.
 */
@Service
public class PreferenceDefaultsService {

	/**
	 * 여행에서 답한 것을 계정 기본값으로 이어받을 차원 (S15P21E201-639).
	 *
	 * <p>기준은 <b>"다음 여행에도 같은가"</b> 하나다.
	 *
	 * <table border="1">
	 * <caption>왜 이 다섯인가</caption>
	 * <tr><th>차원</th><th>왜 넣나</th></tr>
	 * <tr><td>{@code LOCALITY}</td><td>대표 명소냐 현지인 공간이냐 — 취향이라 잘 안 변한다</td></tr>
	 * <tr><td>{@code QUIETNESS}</td><td>혼잡을 얼마나 피하나 — 성향이다</td></tr>
	 * <tr><td>{@code TOURIST_PREFERENCE}</td><td>숨은 곳이냐 대표 관광지냐 — 성향이다</td></tr>
	 * <tr><td>{@code FOOD_PREFERENCE}</td><td>무엇을 먹나 — 대체로 안 변한다</td></tr>
	 * <tr><td>{@code SLOPE_PREFERENCE}</td><td>🔴 <b>몸에 붙은 것</b>이라 거의 안 변한다</td></tr>
	 * </table>
	 *
	 * <h3>🔴 일부러 뺀 넷</h3>
	 *
	 * <ul>
	 * <li>{@code CATEGORY} — 이번엔 바다, 다음엔 문화. <b>여행의 성격</b>이지 사람의 성향이 아니다</li>
	 * <li>{@code ATMOSPHERE} — 혼자 갈 때와 부모님 모실 때가 다르다</li>
	 * <li>🔴 {@code SHADE_PREFERENCE} — <b>계절에 뒤집힌다.</b> 9월 부산에서는 그늘을 찾지만
	 *     12월에는 볕을 찾는다. 이어받으면 <b>겨울에 그늘길을 추천</b>하게 된다.
	 *     이어받으려면 "언제 답했나" 를 같이 보고 철이 바뀌면 다시 묻는 규칙이 먼저 필요하다</li>
	 * <li>{@code SPEND_PROFILE} — 이미 {@code SpendProfileService} 가 계정에 저장한다.
	 *     여기서 또 건드리면 그쪽의 부분 갱신과 겹쳐 서로 덮는다</li>
	 * </ul>
	 *
	 * <p>🔴 알레르기·식단은 여기 없다. 그것은 취향이 아니라 <b>제약</b>이고(다른 표다),
	 * 화면이 <i>"이 여행을 준비하는 동안에만 사용하며 저장하지 않는다"</i> 고 약속해 두었다.
	 * 계정에 남기려면 그 문구를 먼저 바꾸고 {@code HEALTH_CONSTRAINTS} 동의를 다시 물어야 한다.
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
	 * 계정 기본 취향을 새 판으로 바꾼다.
	 *
	 * <p>🔴 <b>부분 갱신이 아니다.</b> 넘긴 목록이 그 판의 전부이고, 빠진 차원은 "값이
	 * 없다" 가 된다. 부분 갱신으로 만들면 "이 차원을 지웠다" 와 "이 차원을 안 보냈다" 를
	 * 구분할 수 없고, 그 둘은 사용자에게 정반대다.
	 */
	public PreferenceSnapshot replace(String userId, List<PreferenceSnapshot.PreferenceAnswer> answers) {
		Instant now = Instant.now(this.clock);
		return this.repository.saveUserDefaults(userId, (answers == null) ? List.of() : answers, now);
	}

	/**
	 * 여행 답 위에 계정 기본값을 겹친다. 규칙은 이 클래스 javadoc 의 표 그대로다.
	 *
	 * <p>🔴 계정 기본값 쪽에서는 {@code SELECTED} 만 쓴다. 기본값에 남아 있는
	 * {@code SKIPPED}·{@code UNKNOWN} 은 <b>값이 없다</b>는 뜻이라 채울 것이 없다 —
	 * 그것을 옮기면 여행의 {@code UNKNOWN} 을 다른 종류의 빈 값으로 바꾸는 것뿐이다.
	 *
	 * @param tripAnswers 이 여행에서 받은 답. {@code null} 이면 빈 목록으로 본다
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

		// 🔴 차원 이름을 대문자로 맞춰 견준다. 화면이 camelCase 로 보내는 이름과 DB CHECK 가
		//    요구하는 대문자가 아직 섞여 있어서(TripCreationService 의 transport 주석 참고),
		//    대소문자를 그대로 비교하면 같은 차원이 둘로 보여 기본값이 여행 답을 덮어쓴다.
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
			// SELECTED · SKIPPED 는 그대로 둔다 — 위 표.
		}
		return List.copyOf(merged.values());
	}

	/**
	 * 이 여행에서 <b>고른</b> 답을 계정 기본값으로 이어받는다 (S15P21E201-639).
	 *
	 * <p>{@link #overlayDefaults} 의 반대 방향이다. 옮기는 조건은 둘이다.
	 * <ol>
	 * <li>여행에서 <b>실제로 고른</b> 답이다 ({@code SELECTED})
	 *     — 🔴 {@code SKIPPED} 는 <i>"이번 여행만 이 조건 빼고"</i> 라서 계정을 안 건드린다.
	 *     {@link #overlayDefaults} 가 건너뜀을 되살리지 않는 것과 짝이다.
	 *     {@code UNKNOWN} 은 아예 안 물어봤으니 사용자의 의사가 없다</li>
	 * <li>{@link #CARRY_OVER} 에 있는 차원이다 — 다음 여행에도 같을 것들만</li>
	 * </ol>
	 *
	 * <p>🔴 <b>계정에 이미 값이 있어도 옮긴다.</b> 처음에는 "빈칸만" 으로 만들었다가
	 * 되돌렸다 — 고치는 화면이 없어서 사용자가 첫 답에 영구히 갇히기 때문이다.
	 * 까닭은 이 클래스 javadoc 의 「빈칸만 채운다 로 먼저 만들었다가 되돌렸다」 절에 있다.
	 *
	 * <p>🔴 <b>값이 같으면 저장하지 않는다.</b> 화면이 채워진 값을 그대로 돌려보내므로,
	 * 사용자가 아무것도 안 고쳐도 같은 답이 매번 올라온다. 그때마다 판을 새로 쓰면
	 * {@code created_at} 만 다른 판이 쌓이고 <b>"언제 정한 취향인가" 를 나중에 못 본다.</b>
	 *
	 * <p>🔴 <b>전체 교체로 저장한다.</b> {@link #replace} 는 부분 갱신이 아니므로, 지금 계정에
	 * 있는 답을 전부 읽어 바뀐 것을 얹은 <b>전부</b>를 다시 보낸다. 바뀐 것만 보내면 나머지
	 * 차원이 사라진다 ({@link #replace} javadoc 의 경고가 그것이다).
	 *
	 * @param tripAnswers 이 여행에서 받은 답 (겹치기 <b>전</b>의 것이어야 한다 — 겹친 뒤의
	 *        목록에는 계정 기본값이 섞여 있어, 사용자가 답하지 않은 차원까지 "고른 것" 으로
	 *        보이게 된다)
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

	private static String key(String dimension) {
		return dimension.toUpperCase(Locale.ROOT);
	}
}
