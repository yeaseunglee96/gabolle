package com.gabolle.backend.batch.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 접을 사람 목록 — MLOps Phase 1.
 *
 * <h2>🔴 {@code truncated} 가 있는 이유</h2>
 *
 * 이 응답에서 {@code count} 가 상한과 같을 때, 그것만으로는 <b>"마침 그만큼 있었다"</b> 와
 * <b>"더 있는데 잘렸다"</b> 를 구분할 수 없다. 구분이 없으면 밀린 사람이 하루에 상한만큼씩만
 * 빠지면서 조용히 미뤄지고, 배치는 그동안 날마다 초록이다 — 아무도 모른다.
 *
 * <p>이 상황은 평소에는 안 온다. {@code vector_version} 을 올리거나 backfill 을 돌려
 * <b>모두가 한꺼번에 뒤처지는 날</b> 온다. 즉 가장 바쁜 날에 처음 나타난다.
 *
 * @param asOf 어느 시각을 기준으로 고른 목록인가. 요청한 값을 그대로 돌려준다
 * @param count 이 응답에 실린 사람 수. DAG 로그에서 목록을 다 읽지 않고도 규모를 안다
 * @param truncated 상한에 걸려 잘렸는가. 참이면 이 목록 <b>밖에 더 있다</b>
 * @param limit 실제로 적용된 상한. 요청값이 서버 상한을 넘으면 여기로 깎여서 돌아온다
 * @param userIds 사람들. {@code user_id} 순서라 같은 구간을 다시 돌리면 같은 순서다
 */
public record StaleUsersResponse(OffsetDateTime asOf, int count, boolean truncated, int limit, List<UUID> userIds) {
}
