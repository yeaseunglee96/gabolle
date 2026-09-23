package com.gabolle.backend.place.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사용자가 검색 결과에서 고른 장소 한 건 — 앱의 {@code OriginCandidate} 를 그대로 옮긴 모양이다.
 *
 * <p><b>왜 요청마다 이 모양이 필요한가.</b> 앱의 장소 검색은 우리 DB 장소와 카카오 결과를 섞어
 * 보여주는데 카카오 쪽에는 우리 {@code place_id} 가 없다. 그래서 그것을 고르면 지금까지
 * 아무것도 안 실렸다 — 기록은 S15P21E201-1426, 숙소는 S15P21E201-1522 가 같은 결함이다.
 * 서버가 이 스냅샷으로 장소를 찾거나 만든 뒤에만 id 가 생기므로, <b>우리 표에 없는 식별자를
 * 저장하지 않는다</b>는 원칙은 그대로다.
 *
 * <p>🔴 <b>기록과 숙소가 이 한 벌을 같이 쓴다.</b> 두 벌로 두면 한쪽에만 칸이 늘거나 검증이
 * 어긋나고, 그 어긋남은 「어떤 화면에서 고른 장소만 안 붙는」 모양이라 화면에서는 안 보인다.
 *
 * @param source 어느 검색이 준 값인가 — {@code KAKAO_LOCAL} · {@code INTERNAL_FALLBACK}.
 *     🔴 표에 이미 쓰이는 어휘 그대로다. {@code KAKAO} 처럼 새 철자를 들이면 같은 것을 가리키는
 *     값이 두 벌이 되고, {@code uq_place_source} 가 그 둘을 <b>다른 장소로 본다</b>
 * @param externalId 그 원천에서의 식별자. {@code source} 와 짝이 되어 장소 하나를 가리킨다
 * @param lat 위도. {@code lng} 와 함께 있거나 함께 없어야 한다 ({@code ck_place_origin_pair})
 */
public record PlaceSnapshotRequest(
		@NotBlank @Size(max = 50) String source,
		@NotBlank @Size(max = 200) String externalId,
		@NotBlank @Size(max = 200) String name,
		@Size(max = 300) String address,
		@DecimalMin("-90") @DecimalMax("90") Double lat,
		@DecimalMin("-180") @DecimalMax("180") Double lng,
		@Size(max = 50) String category) {
}
