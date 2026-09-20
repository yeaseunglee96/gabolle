package com.gabolle.backend.batch.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 접을 사람 목록.
 *
 * @param asOf 어느 시각을 기준으로 고른 목록인가. 요청한 값을 그대로 돌려준다
 * @param truncated 상한에 걸려 잘렸는가. 참이면 이 목록 밖에 더 있다. {@code count} 가 상한과
 *     같다는 것만으로는 "마침 그만큼" 과 "더 있는데 잘렸다" 를 구분할 수 없어서 있다
 * @param limit 실제로 적용된 상한. 요청값이 서버 상한을 넘으면 여기로 깎여서 돌아온다
 * @param userIds {@code user_id} 순서라 같은 구간을 다시 돌리면 같은 순서다
 */
public record StaleUsersResponse(OffsetDateTime asOf, int count, boolean truncated, int limit, List<UUID> userIds) {
}
