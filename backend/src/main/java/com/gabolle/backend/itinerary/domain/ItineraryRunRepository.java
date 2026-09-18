package com.gabolle.backend.itinerary.domain;

import java.util.List;
import java.util.Optional;

/**
 * 일정 진행 저장소 — S15P21E201-1325.
 *
 * <p>{@link ItineraryItemActualRepository} 와 같은 이유로 <b>인터페이스만</b> 도메인에 두고
 * JPA·Spring 을 import 하지 않는다.
 *
 * <h2>왜 {@link ItineraryRepository} 에 더하지 않았나</h2>
 * 그 저장소가 다루는 것은 <b>판</b>이다. 진행 상태는 판 체인 밖에 있다 — 일정을 고쳐
 * 새 판이 생겨도 「지금 두 번째를 향하고 있다」는 그대로다. 저장 단위가 다르면 저장소도
 * 다르게 둔다.
 */
public interface ItineraryRunRepository {

	/** 없으면 비어 있다 — 그때는 {@link ItineraryRun#planned} 로 읽는다. */
	Optional<ItineraryRun> find(String itineraryId);

	/**
	 * 덮어쓴다 — 없으면 만들고 있으면 바꾼다.
	 *
	 * <p>🔴 「찾아서 없으면 넣는다」로 하면 두 요청이 겹칠 때 기본키 위반이 나고, PostgreSQL
	 * 은 문장 하나가 실패하면 그 트랜잭션 전체를 못 쓰게 만든다. 출발을 두 번 누르는 것은
	 * 정상 경로라 반드시 겹친다.
	 */
	ItineraryRun upsert(ItineraryRun run);

	/** 일어난 일을 하나 쌓는다. 덮어쓰지 않는다. */
	ItineraryStopEvent append(ItineraryStopEvent event);

	/** 그 일정에 쌓인 사건 전부. 시간 순이다. */
	List<ItineraryStopEvent> findEvents(String itineraryId);
}
