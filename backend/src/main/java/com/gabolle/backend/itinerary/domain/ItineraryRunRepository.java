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

	/**
	 * 궤적을 쌓는다. 같은 점이 두 번 와도 한 번만 남는다 — 배치 업로드는 재시도가 정상
	 * 경로라 겹치는 것이 예외가 아니다.
	 *
	 * @return 실제로 새로 들어간 점의 수. 보낸 수와 다르면 겹친 것이 있었다는 뜻이다
	 */
	int saveAllPings(List<ItineraryRunPing> pings);

	/**
	 * 그 사람의 궤적을 전부 지운다 — 탈퇴할 때 부른다.
	 *
	 * <p>🔴 FK 연쇄 삭제에 기대지 않고 따로 부르는 이유는, 위치 기록이 이 저장소에서 제일
	 * 민감한 자료라 <b>지우는 자리가 코드에 보여야</b> 하기 때문이다. 연쇄 삭제는 스키마를
	 * 열어 봐야만 알 수 있고, 그 사이에 표가 하나 끼면 조용히 안 지워진다.
	 */
	int deletePingsOfUser(String userId);
}
