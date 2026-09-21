package com.gabolle.backend.editorial.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.editorial.domain.EditorialPick;
import com.gabolle.backend.editorial.domain.EditorialPickScope;
import com.gabolle.backend.editorial.domain.EditorialPickStatus;

/** {@link EditorialPick} 조회. 발행은 이 서비스가 하지 않는다. */
public interface EditorialPickRepository extends JpaRepository<EditorialPick, UUID> {

	/**
	 * 지금 내보낼 수 있는 Pick 을 {@code displayOrder} 순으로.
	 *
	 * <p> 정렬에 {@code pickKey} 를 덧붙인다. {@code displayOrder} 가 같은 Pick 이 둘
	 * 있으면 순서가 실행마다 달라지고, 그러면 같은 신규 사용자가 새로고침할 때마다 다른
	 * 기준선을 본다. 재현되지 않는 결과는 "왜 이게 떴나" 에 답할 수 없다.
	 */
	@Query("""
			SELECT p FROM EditorialPick p
			WHERE p.status = :status AND p.scope = :scope
			ORDER BY p.displayOrder ASC, p.pickKey ASC
			""")
	List<EditorialPick> findPublishedByScope(@Param("status") EditorialPickStatus status,
			@Param("scope") EditorialPickScope scope);

	/** 이름으로 지금 발행된 판 하나. {@code uq_editorial_pick_published} 가 하나임을 보장한다. */
	@Query("""
			SELECT p FROM EditorialPick p
			WHERE p.pickKey = :pickKey AND p.status = :status
			""")
	Optional<EditorialPick> findPublishedByKey(@Param("pickKey") String pickKey,
			@Param("status") EditorialPickStatus status);
}
