package com.gabolle.backend.batch.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치가 "누구를 접어야 하나" 를 고르고, 고른 사람들을 하나씩 접는다 — MLOps Phase 1.
 *
 * <h2>🔴 고르는 질의는 헐렁하고, 판정은 {@link TasteVectorFoldService} 가 한다</h2>
 *
 * 여기서 하는 일은 "접을 필요가 <b>있을 수도 있는</b> 사람" 을 싸게 긁어 오는 것이다.
 * 정말 접을 것이 있는지는 사람마다 설문·행동을 봐야 알고, 그 판정을 이 질의에 밀어 넣으면
 * 같은 규칙이 SQL 과 Java 두 곳에 생긴다. 둘은 반드시 어긋난다.
 *
 * <p>그래서 여기서는 <b>표시가 뒤처진 사람</b>만 고르고, 접는 쪽이
 * {@code REBUILT} · {@code WATERMARK_ADVANCED} · {@code NOTHING_TO_FOLD} 를 가른다.
 * 헛걸음의 비용은 조회 한 번이고, 규칙이 두 벌이 되는 비용은 조용한 오류다.
 *
 * <h2>🔴 지운 계정은 고르지 않는다</h2>
 *
 * {@code app_user.deleted_at} 이 찍힌 사람을 접으면, <b>잊어 달라고 한 사람의 취향을 다시
 * 계산해서 새 행으로 적는 것</b>이 된다. 탈퇴 처리가 지운 것을 배치가 되살리는 셈이라
 * 기능 결함이 아니라 개인정보 사고다. 배치는 사람이 안 보는 시간에 돌기 때문에 아무도
 * 눈치채지 못한다.
 */
@Service
@Profile({ "db", "dev" })
public class TasteVectorBatchService {

	/** 한 번에 넘겨받을 사람 수의 상한. 요청이 이보다 크게 오면 잘라 낸다. */
	public static final int MAX_BATCH = 2000;

	private final JdbcTemplate jdbc;

	private final TasteVectorFoldService foldService;

	public TasteVectorBatchService(JdbcTemplate jdbc, TasteVectorFoldService foldService) {
		this.jdbc = jdbc;
		this.foldService = foldService;
	}

	/**
	 * 표시가 {@code asOf} 보다 뒤처진 사람들.
	 *
	 * <p>세 경우가 걸린다.
	 * <ul>
	 * <li>현재 판이 아예 없다 — 처음 접는 사람</li>
	 * <li>현재 판에 표시가 없다 — 행동을 한 번도 안 본 판</li>
	 * <li>표시가 {@code asOf} 보다 이르다 — 그 사이에 무언가 도착했을 수 있다</li>
	 * </ul>
	 *
	 * <p>🔴 {@code user_id} 로 정렬한다. 시각으로 정렬하면 같은 구간을 두 번 돌릴 때
	 * 순서가 달라져서, 실패를 재현하려 할 때 같은 배치가 안 나온다.
	 */
	@Transactional(readOnly = true)
	public StalePage staleUsers(OffsetDateTime asOf, int limit) {
		String sql = """
				SELECT u.user_id
				  FROM app_user u
				  LEFT JOIN user_taste_vector v
				         ON v.user_id = u.user_id
				        AND v.superseded_at IS NULL
				 WHERE u.deleted_at IS NULL
				   AND (v.taste_vector_id IS NULL
				        OR v.observed_until IS NULL
				        OR v.observed_until < ?)
				 ORDER BY u.user_id
				 LIMIT ?
				""";
		int capped = clamp(limit);

		// 🔴 상한보다 **하나 더** 받아 본다. 딱 상한만큼 왔다는 사실만으로는 "마침 그만큼
		//    있었다" 와 "더 있는데 잘렸다" 를 구분할 수 없다. 그 구분이 없으면 밀린 사람이
		//    하루에 상한만큼씩만 빠지면서 조용히 미뤄진다 — 배치는 날마다 초록이다.
		//    한 줄 더 읽는 값으로 그 침묵을 없앤다.
		List<UUID> rows = this.jdbc.queryForList(sql, UUID.class, asOf, capped + 1);
		if (rows.size() > capped) {
			return new StalePage(List.copyOf(rows.subList(0, capped)), true, capped);
		}
		return new StalePage(List.copyOf(rows), false, capped);
	}

	/**
	 * 뒤처진 사람 목록 한 쪽(page — 전체 중 이번에 넘겨주는 만큼).
	 *
	 * @param userIds 이번에 넘겨줄 사람들
	 * @param truncated 상한에 걸려 잘렸는가. 참이면 이 목록 <b>밖에 더 있다</b>
	 * @param limit 실제로 적용된 상한. 요청값이 {@link #MAX_BATCH} 를 넘으면 여기로 깎인다
	 */
	public record StalePage(List<UUID> userIds, boolean truncated, int limit) {
	}

	/**
	 * 넘겨받은 사람들을 하나씩 접는다.
	 *
	 * <p>🔴 <b>트랜잭션을 여기 걸지 않는다.</b> {@link TasteVectorFoldService#fold} 각각이
	 * 자기 트랜잭션이다. 여기에 걸면 한 사람이 실패할 때 앞서 성공한 전부가 되돌아가고,
	 * 재시도하면 같은 사람에서 또 죽으므로 배치가 영영 안 끝난다.
	 *
	 * <p>🔴 그래서 <b>한 사람의 실패가 배치를 멈추지 않는다.</b> 실패는 세어서 돌려주고,
	 * 부르는 쪽(Airflow)이 그 수를 보고 실패시킬지 정한다. 여기서 예외를 던져 버리면
	 * 나머지 사람들은 다음 실행까지 방치되는데, 그 사이 화면은 낡은 값을 보여준다.
	 */
	public TasteVectorBatchReport rebuild(List<UUID> userIds, OffsetDateTime asOf) {
		TasteVectorBatchReport.Builder report = TasteVectorBatchReport.builder(asOf);

		for (UUID userId : userIds.stream().distinct().limit(MAX_BATCH).toList()) {
			try {
				report.record(this.foldService.fold(userId, asOf));
			}
			catch (RuntimeException ex) {
				report.recordFailure(userId, ex);
			}
		}
		return report.build();
	}

	private static int clamp(int limit) {
		if (limit < 1) {
			return 1;
		}
		return Math.min(limit, MAX_BATCH);
	}
}
