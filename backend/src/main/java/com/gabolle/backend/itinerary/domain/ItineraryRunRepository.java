package com.gabolle.backend.itinerary.domain;

import java.util.List;
import java.util.Optional;

/**
 * 일정 진행 저장소. 도메인에는 인터페이스만 두고 JPA·Spring 을 import 하지 않는다.
 *
 * <p>{@link ItineraryRepository} 와 나눠 둔 이유는 저장 단위가 다르기 때문이다. 그쪽은
 * 판을 다루고, 진행 상태는 판 체인 밖에 있다 — 일정을 고쳐 새 판이 생겨도 진행 상태는
 * 그대로다.
 */
public interface ItineraryRunRepository {

	/** 없으면 비어 있다 — 그때는 {@link ItineraryRun#planned} 로 읽는다. */
	Optional<ItineraryRun> find(String itineraryId);

	/**
	 * 없으면 만들고 있으면 바꾼다. 「찾아서 없으면 넣는다」로 나눠 쓰지 않는다 — 출발을
	 * 두 번 누르는 것이 정상 경로라 요청이 반드시 겹치고, 기본키 위반 한 번이면 PostgreSQL
	 * 이 트랜잭션 전체를 못 쓰게 만든다.
	 */
	ItineraryRun upsert(ItineraryRun run);

	/** 일어난 일을 하나 쌓는다. 덮어쓰지 않는다. */
	ItineraryStopEvent append(ItineraryStopEvent event);

	/** 그 일정에 쌓인 사건 전부. 시간 순이다. */
	List<ItineraryStopEvent> findEvents(String itineraryId);
}
