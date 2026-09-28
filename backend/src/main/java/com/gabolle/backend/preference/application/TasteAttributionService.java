package com.gabolle.backend.preference.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.preference.domain.TasteSignal;
import com.gabolle.backend.preference.domain.UserTasteWeight;

/**
 * 행동 이벤트 <b>하나</b>를 취향 벡터에 증분으로 반영한다 (S15P21E201-1500).
 *
 * <h2>배치와 무엇이 다른가</h2>
 *
 * {@code BehaviorTasteFolder} 는 접을 때마다 <b>전 이력</b>을 다시 읽어 새 판을 통째로 만든다.
 * 이쪽은 이벤트 하나만 들고 와서 <b>지금 판</b>을 고친다. 그래서 필요한 것이 둘이다.
 *
 * <ul>
 * <li>{@code user_taste_weight.raw} — 눌러 담기 전의 합. 더하기는 언제나 여기에 한다</li>
 * <li>{@code user_place_taste_state} — {@code (사람, 장소)} 의 <b>직전</b> 상태. 「하트를 껐다」가
 * 왔을 때 얼마를 빼야 하는지는 직전이 무엇이었나에 달려 있다</li>
 * </ul>
 *
 * <h2>🔴 벡터를 만들지는 않는다</h2>
 *
 * 아직 한 번도 접힌 적 없는 사람은 <b>그냥 넘어간다.</b> 판을 새로 만드는 일(판 번호, 설문
 * 스냅샷 연결, 옛 판 내리기)은 {@code TasteVectorFoldService} 의 몫이고, 그것을 여기에 다시
 * 만들면 판을 만드는 규칙이 두 곳에 생긴다. 지금은 배치가 계속 돌므로 곧 만들어지고, 그
 * 뒤부터 이쪽이 이어받는다.
 *
 * <p>배치를 걷어내는 S15P21E201-1501 에서 이 자리를 옮겨야 한다 — <b>그때까지 이 클래스만으로는
 * 새 사용자의 취향이 시작되지 않는다.</b>
 *
 * <p>🟢 <b>2026-09-23 — 설문을 한 사람은 옮겼다</b> (S15P21E201-1515). 설문이 저장되면 커밋 직후
 * {@code TasteFoldOnPreferenceSave} 가 그 사람을 접어 판을 만든다. 🔴 <b>설문 없이 행동만 있는
 * 사람은 아직 배치가 첫 판을 만든다</b> — 그 사람에게는 판을 만들 계기가 이벤트뿐인데, 여기서
 * 만들지 않기로 한 이유는 위와 같다. 배치를 걷기 전에 이 자리를 정해야 한다.
 *
 * <h2>순서는 보장된다</h2>
 *
 * 카프카 파티션 키가 사용자라, 한 사람의 이벤트는 <b>보낸 순서대로</b> 온다
 * ({@code EventIngestService.partitionKeyOf}). 상태 전이를 순서대로 밟을 수 있는 근거가 그것이다.
 * 같은 이유로 한 사용자에 대해 두 스레드가 동시에 이 코드를 돌지 않는다.
 *
 * <h2>🔴 {@code @Profile} 이 붙은 이유</h2>
 *
 * {@code JdbcTemplate} 은 데이터소스가 있을 때만 생긴다. 프로필을 안 걸었더니 <b>DB 가 없는
 * 컨텍스트가 이 빈을 만들려다 죽었다</b> — 기동 검사·슬라이스 검사 여섯이 한꺼번에 빨개졌다.
 * {@code BehaviorTasteFolder} 가 같은 이유로 같은 프로필을 달고 있다.
 */
@Component
@Profile({ "db", "dev" })
public class TasteAttributionService {

	private static final Logger log = LoggerFactory.getLogger(TasteAttributionService.class);

	/** 지금 쓰는 판. 옛 판은 {@code superseded_at} 이 채워져 있다. */
	private static final String CURRENT_VECTOR_SQL = """
			SELECT taste_vector_id FROM user_taste_vector
			 WHERE user_id = ? AND superseded_at IS NULL
			""";

	/**
	 * 이 장소가 어느 {@code (차원, 코드)} 로 귀속되는가.
	 *
	 * <p>조인은 {@code BehaviorTasteFolder} 의 것과 같다 — 장소 표식을 대조표로 옮긴다. 다른
	 * 점은 <b>장소 하나</b>만 본다는 것뿐이다.
	 *
	 * <p>🔴 태그형({@code TAG_OVERLAP})만 고른다. 점수형은 배치가 일부러 뺐다 — 좋아한 장소들의
	 * 점수 평균은 취향이 아니라 주변 지리를 반영한다. 여기서도 같은 판단을 따른다.
	 */
	private static final String COMPONENTS_OF_PLACE_SQL = """
			SELECT m.user_input_code AS dimension, pf.feature_key AS code
			  FROM place_feature pf
			  JOIN user_place_code_map m
			    ON m.place_feature_type = pf.feature_type
			   AND m.user_input_kind = 'PREFERENCE'
			   AND m.match_kind = 'TAG_OVERLAP'
			 WHERE pf.place_id = ?
			   AND pf.feature_key IS NOT NULL
			   AND pf.evidence_status IN ('VERIFIED', 'ESTIMATED')
			   AND (pf.value IS NULL OR pf.value::text <> 'false')
			""";

	private final JdbcTemplate jdbc;

	public TasteAttributionService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 이벤트 하나를 반영한다.
	 *
	 * <p>동의는 여기서 안 본다 — 개인화를 끈 사람의 이벤트는 애초에 적히지 않는다
	 * ({@code EventIngestService.collectsBehaviorOf}). 두 곳에 두면 어긋난다.
	 *
	 * @param at 이벤트가 일어난 시각. 지어내지 않고 헤더에 실려 온 것을 쓴다
	 */
	@Transactional
	public void apply(UUID userId, String eventType, UUID placeId, OffsetDateTime at) {
		if (userId == null || placeId == null || eventType == null) {
			return;
		}

		UUID vectorId = currentVectorId(userId);
		if (vectorId == null) {
			// 아직 접힌 적 없는 사람. 배치가 첫 판을 만들면 그때부터 이어받는다 (위 머리말).
			log.debug("event=TASTE_SKIPPED_NO_VECTOR user={} type={}", userId, eventType);
			return;
		}

		Delta delta = deltaOf(userId, eventType, placeId, at);
		if (delta == null || (delta.raw() == 0.0 && delta.support() == 0)) {
			return;
		}

		List<Component> components = componentsOf(placeId);
		for (Component component : components) {
			applyToComponent(vectorId, component, delta, at);
		}
		log.debug("event=TASTE_APPLIED user={} type={} place={} 성분={} rawΔ={} supportΔ={}", userId, eventType,
				placeId, components.size(), delta.raw(), delta.support());
	}

	/**
	 * 이 이벤트가 바꾸는 양.
	 *
	 * <p>🔴 상태 이벤트는 <b>차이</b>를 낸다. 「하트를 껐다」가 얼마를 빼는지는 직전이 무엇이었나에
	 * 달려 있고, 「이미 하트인데 또 하트」는 <b>아무것도 안 바꾼다</b> — 하트 한 번이 이벤트
	 * 두 건으로 오는 것(S15P21E201-1485)이 여기서 저절로 한 번으로 접힌다.
	 */
	private Delta deltaOf(UUID userId, String eventType, UUID placeId, OffsetDateTime at) {
		if (TasteSignal.isState(eventType)) {
			String previous = previousState(userId, placeId);
			if (eventType.equals(previous)) {
				return null;
			}
			saveState(userId, placeId, eventType, at);
			return new Delta(forceOf(eventType) - forceOf(previous), countedOf(eventType) - countedOf(previous));
		}
		if (TasteSignal.isRepeatable(eventType)) {
			Double contribution = TasteSignal.contributionOf(eventType);
			return (contribution == null) ? null : new Delta(contribution, 1);
		}
		// 장소가 여럿 실리는 이벤트(itinerary_remove)는 아직 이 길로 안 온다. 부르는 쪽이
		// 장소 하나를 뽑아 주는 모양이라, 배열을 펼치는 것은 그 이벤트를 실제로 켤 때 더한다.
		return null;
	}

	/**
	 * 한 성분에 차이를 적용한다.
	 *
	 * <p>🔴 <b>뒷받침이 0 이하로 내려가면 행을 지운다.</b> {@code ck_user_taste_weight_interaction_has_support}
	 * 가 「행동에서 나왔다면서 관측이 없는」 행을 DB 에서 막는다. 하트를 껐는데 행이 0 으로
	 * 남아 있으면 그 제약에 걸려 갱신 자체가 실패한다. 지우는 것이 뜻으로도 맞다 — 반영할
	 * 관측이 하나도 안 남았으면 그 성분은 <b>없는</b> 것이다.
	 */
	private void applyToComponent(UUID vectorId, Component component, Delta delta, OffsetDateTime at) {
		List<Existing> found = this.jdbc.query("""
				SELECT raw, support FROM user_taste_weight
				 WHERE taste_vector_id = ? AND dimension = ? AND code = ? AND evidence = 'INTERACTION'
				""", (rs, row) -> new Existing(rs.getDouble("raw"), rs.getInt("support")), vectorId,
				component.dimension(), component.code());

		if (found.isEmpty()) {
			if (delta.support() <= 0) {
				// 없던 성분을 「빼는」 이벤트가 왔다. 반영할 것이 없다 — 지울 것도 없다.
				return;
			}
			this.jdbc.update("""
					INSERT INTO user_taste_weight
					       (taste_vector_id, dimension, code, evidence, raw, weight, support, updated_at)
					VALUES (?, ?, ?, 'INTERACTION', ?, ?, ?, ?)
					""", vectorId, component.dimension(), component.code(), delta.raw(),
					UserTasteWeight.confidence(delta.raw()), delta.support(), at);
			return;
		}

		Existing existing = found.get(0);
		double raw = existing.raw() + delta.raw();
		int support = existing.support() + delta.support();

		if (support <= 0) {
			this.jdbc.update("""
					DELETE FROM user_taste_weight
					 WHERE taste_vector_id = ? AND dimension = ? AND code = ? AND evidence = 'INTERACTION'
					""", vectorId, component.dimension(), component.code());
			return;
		}

		this.jdbc.update("""
				UPDATE user_taste_weight
				   SET raw = ?, weight = ?, support = ?, updated_at = ?
				 WHERE taste_vector_id = ? AND dimension = ? AND code = ? AND evidence = 'INTERACTION'
				""", raw, UserTasteWeight.confidence(raw), support, at, vectorId, component.dimension(),
				component.code());
	}

	private UUID currentVectorId(UUID userId) {
		List<UUID> found = this.jdbc.query(CURRENT_VECTOR_SQL, (rs, row) -> rs.getObject(1, UUID.class), userId);
		return found.isEmpty() ? null : found.get(0);
	}

	private List<Component> componentsOf(UUID placeId) {
		return this.jdbc.query(COMPONENTS_OF_PLACE_SQL,
				(rs, row) -> new Component(rs.getString("dimension"), rs.getString("code")), placeId);
	}

	private String previousState(UUID userId, UUID placeId) {
		List<String> found = this.jdbc.query(
				"SELECT event_type FROM user_place_taste_state WHERE user_id = ? AND place_id = ?",
				(rs, row) -> rs.getString(1), userId, placeId);
		return found.isEmpty() ? null : found.get(0);
	}

	private void saveState(UUID userId, UUID placeId, String eventType, OffsetDateTime at) {
		this.jdbc.update("""
				INSERT INTO user_place_taste_state (user_id, place_id, event_type, updated_at)
				VALUES (?, ?, ?, ?)
				ON CONFLICT (user_id, place_id)
				DO UPDATE SET event_type = EXCLUDED.event_type, updated_at = EXCLUDED.updated_at
				""", userId, placeId, eventType, at);
	}

	/** 이 상태가 미는 힘. 상태가 없거나 「끔」이면 0 이다. */
	private static double forceOf(String eventType) {
		Double contribution = (eventType == null) ? null : TasteSignal.contributionOf(eventType);
		return (contribution == null) ? 0.0 : contribution;
	}

	/**
	 * 이 상태가 관측으로 세어지는가 (1) 아닌가 (0).
	 *
	 * <p>「끔」은 0 이다 — 하트를 껐다는 것은 <b>관측이 없는 상태</b>이지 「0 만큼의 관측」이
	 * 아니다. {@link TasteSignal#contributionOf} 가 0.0 이 아니라 {@code null} 을 주는 이유가 이것이다.
	 */
	private static int countedOf(String eventType) {
		return (eventType == null || TasteSignal.contributionOf(eventType) == null) ? 0 : 1;
	}

	private record Component(String dimension, String code) {
	}

	private record Existing(double raw, int support) {
	}

	private record Delta(double raw, int support) {
	}

}
