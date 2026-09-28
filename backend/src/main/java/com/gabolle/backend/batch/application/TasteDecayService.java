package com.gabolle.backend.batch.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.gabolle.backend.preference.domain.UserTasteWeight;

/**
 * 옛 행동 신호를 하루에 한 번 옅게 만든다 (S15P21E201-1501).
 *
 * <h2>«들어오는 값» 이 아니라 «쌓인 값» 에 건다</h2>
 *
 * <pre>
 * raw_새로 = raw_기존 × 0.97  +  오늘 들어온 기여값들
 *            └─ 이 클래스 ─┘     └─ TasteAttributionService ─┘
 * </pre>
 *
 * 하루가 지날 때마다 <b>이미 있던 것만</b> 줄고 오늘 온 것은 안 줄어서, 각 이벤트의 무게가
 * 저절로 {@code 계수^(며칠 지났나)} 가 된다. 🔴 <b>언제 들어온 이벤트인지 기억할 필요가
 * 없다</b> — 상태가 숫자 하나로 끝난다. 이벤트마다 날짜를 들고 다니며 가중치를 계산하는
 * 방식과 결과는 같고 비용은 비교가 안 된다.
 *
 * <h2>🔴 설문 행은 안 건드린다</h2>
 *
 * 사람이 직접 고른 값은 시간이 지났다고 옅어질 이유가 없다. 조건이
 * {@code evidence = 'INTERACTION'} 하나뿐인 것이 그 뜻이다.
 *
 * <h2>사람 수만큼 반복하지 않는다</h2>
 *
 * 명령 한 줄이 표 전체를 훑는다. 사람이 만 명이든 십만 명이든 질의는 하나다.
 *
 * <h2>🔴 아직 안 하는 것 — 뒷받침은 안 줄인다</h2>
 *
 * {@code support}(관측 수)는 그대로 둔다. 그래서 {@code raw} 가 0 에 가까워져도 행은 남고,
 * 채점기의 분모에는 계속 들어간다({@code ratio = 합 / 성분 수}). 배치가 하던 일과는 다르다 —
 * 그쪽은 이벤트가 사라지면 관측 수도 함께 줄었다.
 *
 * <p>줄이는 방법이 여럿이라(정수를 깎기·문턱 아래 행 지우기) <b>지금 고르지 않는다.</b>
 * 감쇠가 실제로 도는 것을 보고 분모가 문제가 되는지 먼저 재는 편이 낫다. 지어내면 그것이
 * 계약이 된다.
 */
@Component
@Profile({ "db", "dev" })
public class TasteDecayService {

	private static final Logger log = LoggerFactory.getLogger(TasteDecayService.class);

	/**
	 * 🔴 오른쪽의 {@code raw} 는 전부 <b>갱신 전</b> 값이다. 한 {@code UPDATE} 안에서 컬럼을
	 * 읽으면 예전 값이 오는 것이 SQL 의 규칙이라, {@code weight} 가 방금 곱한 값을 다시 곱하는
	 * 일은 없다.
	 *
	 * <p>{@code weight} 를 {@code raw} 에서 <b>다시 만든다</b> — 눌러 담은 값에 계수를 곱하면
	 * 뜻이 없다. 변환 규칙은 {@link UserTasteWeight#confidence(double)} 와 같은 식이고, K 는
	 * 파라미터로 넘겨 자바 상수와 한 벌로 유지한다.
	 */
	private static final String DECAY_SQL = """
			UPDATE user_taste_weight
			   SET raw = raw * ?,
			       weight = (raw * ?) / (abs(raw * ?) + ?),
			       updated_at = ?
			 WHERE evidence = 'INTERACTION'
			""";

	private final JdbcTemplate jdbc;

	private final TasteDecayProperties properties;

	private final Clock clock;

	public TasteDecayService(JdbcTemplate jdbc, TasteDecayProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 한 번 감쇠시킨다.
	 *
	 * <p>🔴 <b>이상한 계수를 조용히 고쳐 쓰지 않는다.</b> 0 이하면 취향이 통째로 뒤집히거나
	 * 사라지고, 1 을 넘으면 옛 신호가 <b>커진다.</b> 둘 다 오류 없이 추천만 이상해지는 종류라
	 * 그 자리에서 거부한다.
	 *
	 * @return 옅어진 행 수. 0 이면 감쇠가 없거나({@code 계수 = 1}) 행동 성분이 아직 없는 것이다
	 */
	public int decayOnce() {
		double factor = this.properties.getDailyFactor();
		if (!(factor > 0.0) || factor > 1.0) {
			throw new IllegalStateException(
					"감쇠 계수는 0 초과 1 이하여야 한다. gabolle.taste.decay.daily-factor=" + factor);
		}
		if (factor == 1.0) {
			// 1.0 은 「감쇠 안 함」이다. 질의를 돌리면 모든 행의 updated_at 만 바뀌어,
			// 「언제 마지막으로 값이 움직였나」를 못 보게 된다.
			return 0;
		}

		OffsetDateTime now = OffsetDateTime.now(this.clock);
		int decayed = this.jdbc.update(DECAY_SQL, factor, factor, factor, UserTasteWeight.CONFIDENCE_K, now);
		if (decayed > 0) {
			log.info("event=TASTE_DECAYED rows={} factor={}", decayed, factor);
		}
		return decayed;
	}

}
