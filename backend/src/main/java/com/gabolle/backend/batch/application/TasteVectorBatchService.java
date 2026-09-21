package com.gabolle.backend.batch.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치가 접을 사람을 고르고, 고른 사람들을 하나씩 접는다.
 *
 * <p>고르는 질의는 일부러 헐렁하다. "접을 필요가 있을 수도 있는 사람" 만 싸게 긁어 오고,
 * 정말 접을 것이 있는지는 {@link TasteVectorFoldService} 가 판정한다. 그 판정을 질의에 밀어
 * 넣으면 같은 규칙이 SQL 과 Java 두 곳에 생기고 반드시 어긋난다. 헛걸음의 비용은 조회 한 번,
 * 규칙이 두 벌이 되는 비용은 조용한 오류다.
 *
 * <p>{@code app_user.deleted_at} 이 찍힌 사람은 고르지 않는다. 접으면 잊어 달라고 한 사람의
 * 취향을 다시 계산해 새 행으로 적는 것이 되고, 배치는 사람이 안 보는 시간에 돌아서 아무도
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
	 * 표시가 {@code asOf} 보다 뒤처진 사람들 — 현재 판이 없거나, 표시가 없거나, 표시가
	 * {@code asOf} 보다 이른 경우.
	 *
	 * <p>{@code user_id} 로 정렬한다. 시각으로 정렬하면 같은 구간을 두 번 돌릴 때 순서가
	 * 달라져서 실패를 재현할 수 없다.
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

		// 상한보다 하나 더 받아 본다. 딱 상한만큼 왔다는 사실만으로는 "마침 그만큼 있었다" 와
		// "더 있는데 잘렸다" 를 구분할 수 없고, 구분이 없으면 밀린 사람이 하루에 상한만큼씩만
		// 빠지면서 조용히 미뤄진다.
		List<UUID> rows = this.jdbc.queryForList(sql, UUID.class, asOf, capped + 1);
		if (rows.size() > capped) {
			return new StalePage(List.copyOf(rows.subList(0, capped)), true, capped);
		}
		return new StalePage(List.copyOf(rows), false, capped);
	}

	/**
	 * @param truncated 상한에 걸려 잘렸는가. 참이면 이 목록 밖에 더 있다
	 * @param limit 실제로 적용된 상한. 요청값이 {@link #MAX_BATCH} 를 넘으면 여기로 깎인다
	 */
	public record StalePage(List<UUID> userIds, boolean truncated, int limit) {
	}

	/**
	 * 넘겨받은 사람들을 하나씩 접는다. 트랜잭션을 여기 걸지 않는다 —
	 * {@link TasteVectorFoldService#fold} 각각이 자기 트랜잭션이다.
	 *
	 * <p>한 사람의 실패가 배치를 멈추지 않는다. 실패는 세어서 돌려주고 부르는 쪽이 그 수를 보고
	 * 실패시킬지 정한다. 여기서 예외를 던지면 나머지는 다음 실행까지 방치된다.
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
