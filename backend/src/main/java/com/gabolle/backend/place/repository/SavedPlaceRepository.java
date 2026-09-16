package com.gabolle.backend.place.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.domain.SavedPlace;

public interface SavedPlaceRepository extends JpaRepository<SavedPlace, UUID> {

	/**
	 * 내가 저장한 장소 — 저장 탭과 홈 캐러셀의 하트 표시를 되살린다. 최근에 저장한 것이 먼저다.
	 *
	 * <h2>상한을 두기로 바꿨다 (2026-09-16, S15P21E201-1037)</h2>
	 *
	 * 처음 만들 때(-1013)의 판단은 <b>상한을 두지 않는다</b>였고, 근거는 「사람이 손으로 하트를
	 * 누른 수만큼이라 사람 손이 상한이고, 끝없이 자라는 목록이 아니다」였다. 그 판단은 한 번에
	 * 얼마나 쌓이는가에 대해서는 맞다.
	 *
	 * <p>바꾼 이유는 <b>이 조회가 도는 자리</b>다. 저장 탭만이 아니라 홈 화면이 뜰 때마다
	 * 하트 표시를 맞추려고 이걸 부른다. 일 년 쓴 계정의 목록 전체가 홈을 열 때마다 오가는
	 * 모양이 되고, 그 무게는 사람이 하트를 몇 개 눌렀는지가 아니라 홈을 몇 번 여는지에 붙는다.
	 *
	 * <p>같은 주석이 「언젠가 쪽나눔이 필요해지면 그때는 <b>더 있다</b> 칸을 함께 넣어야 한다」
	 * 고 적어 뒀다. 그대로 했다 — 부르는 쪽이 「상한+1」을 요청해서 더 있는지 판정하고,
	 * 그 사실을 응답의 {@code hasMore} 로 함께 보낸다.
	 */
	List<SavedPlace> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	boolean existsByUserIdAndPlaceId(UUID userId, UUID placeId);

	void deleteByUserIdAndPlaceId(UUID userId, UUID placeId);

	/**
	 * 없으면 넣고, 이미 있으면 아무것도 안 한다 — S15P21E201-1037.
	 *
	 * <h2>왜 「있는지 보고 없으면 넣는다」가 아닌가</h2>
	 *
	 * 그 방식은 두 요청 사이가 벌어진다. 하트를 빠르게 두 번 누르면 두 요청이 둘 다
	 * {@code exists} 를 통과한 뒤 둘 다 넣으려 하고, {@code uk_saved_place} 에 걸린 쪽이
	 * {@code DataIntegrityViolationException} 이 된다. 이 예외를 잡는 어드바이스가 없어
	 * <b>500</b> 이 나갔다 — {@code PUT} 이 멱등이라고 적어 둔 경로에서.
	 *
	 * <p>자바에서 그 예외를 잡는 방법도 있지만, 트랜잭션 안에서 잡으면 그 트랜잭션은 이미
	 * 되돌리기로 표시돼 뒤따르는 조회가 다시 실패한다. 판정을 <b>DB 한 문장 안에서</b>
	 * 끝내면 예외 자체가 안 생긴다.
	 *
	 * <p>{@code flushAutomatically} 로 앞선 변경을 먼저 내보내고 {@code clearAutomatically} 로
	 * 영속성 컨텍스트를 비운다 — 네이티브 문장은 그 컨텍스트를 거치지 않으므로, 비우지 않으면
	 * 같은 트랜잭션의 다음 조회가 이 문장의 결과를 못 본 낡은 객체를 돌려줄 수 있다.
	 *
	 * @return 실제로 넣었으면 1, 이미 있어서 아무것도 안 했으면 0
	 */
	//
	// @Transactional 을 여기 붙인다 (S15P21E201-1037). @Modifying 질의는 트랜잭션을
	//    요구하는데, Spring Data 는 기본 CRUD 에만 트랜잭션을 걸어 주고 직접 쓴 질의에는
	//    안 걸어 준다. 부르는 서비스가 전부 @Transactional 이라 운영에서는 안 드러나고,
	//    리포지토리를 곧장 부르는 시험에서 "flush 를 처리할 수 없다" 로 터졌다.
	//    운영에서는 부모 트랜잭션에 합류하므로(REQUIRED) 동작이 달라지지 않는다.
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			INSERT INTO saved_place (saved_place_id, user_id, place_id, created_at)
			VALUES (:savedPlaceId, :userId, :placeId, :createdAt)
			ON CONFLICT (user_id, place_id) DO NOTHING
			""", nativeQuery = true)
	int insertIfAbsent(@Param("savedPlaceId") UUID savedPlaceId, @Param("userId") UUID userId,
			@Param("placeId") UUID placeId, @Param("createdAt") OffsetDateTime createdAt);
}
