package com.gabolle.backend.trip.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
 */
@Service
public class PreferenceDefaultsService {

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

	private static String key(String dimension) {
		return dimension.toUpperCase(Locale.ROOT);
	}
}
