package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.loader.ResearchQueueReader;
import com.gabolle.backend.place.loader.SbizRow;

/**
 * 진짜 조사 대기열 파일을 읽어 본다 — S15P21E201-804.
 *
 * <p>{@code ResearchQueueLoaderIntegrationTest} 는 실제 파일에서 <b>세 줄을 떠서</b> 재는데,
 * 그것으로는 "2,355줄 전체가 우리가 생각한 모양인가" 를 알 수 없다. 한 줄만 달라도 조용히
 * 버려지고, 그러면 적재 뒤에 장소가 몇 곳 비는데 아무도 눈치채지 못한다.
 *
 * <h2>🔴 파일이 없으면 건너뛴다 — 실패시키지 않는다</h2>
 * 대기열은 {@code bigData} 쪽 브랜치에 있고 이 저장소({@code back/dev})에는 없다. 그래서 CI 와
 * 대부분의 PC 에서는 이 검사가 <b>건너뛰어진다.</b> 파일을 가진 사람만 재게 하는 것이 요점이고,
 * 없다고 빨갛게 만들면 그 빨강이 일상이 되어 아무도 안 본다.
 *
 * <pre>
 * git show origin/bigData/dev:bigData/research/data/queue.ndjson &gt; /tmp/queue.ndjson
 * GABOLLE_RESEARCH_QUEUE=/tmp/queue.ndjson ./gradlew test --tests '*ResearchQueueRealFileTest'
 * </pre>
 */
class ResearchQueueRealFileTest {

	/** 조사가 끝난 곳의 수 — {@code S15P21E201-760} 완료 보고의 숫자다. */
	private static final int RESEARCHED = 2_355;

	private static Path queueFile() {
		String path = System.getenv("GABOLLE_RESEARCH_QUEUE");
		return (path == null || path.isBlank()) ? null : Path.of(path);
	}

	@Test
	@DisplayName("진짜 대기열 2,355줄이 한 줄도 안 버려지고 읽힌다")
	void everyRealLineIsUsable() {
		Path queue = queueFile();
		assumeTrue(queue != null && Files.isRegularFile(queue),
				"GABOLLE_RESEARCH_QUEUE 가 없다 — 이 검사는 대기열 파일을 가진 사람만 돈다");

		List<SbizRow> rows = new ArrayList<>();
		ResearchQueueReader.Counts counts = ResearchQueueReader.read(queue, 500, rows::addAll);

		assertThat(counts.total()).isEqualTo(RESEARCHED);
		// 🔴 버려진 줄이 0 이어야 한다. 하나라도 버려지면 그만큼 장소가 안 들어가고,
		//    그 사실은 적재 로그를 자세히 읽어야만 보인다.
		assertThat(counts.skippedBroken()).isZero();
		assertThat(counts.skippedNoCoordinate()).isZero();
		assertThat(counts.usable()).isEqualTo(RESEARCHED);
		assertThat(rows).hasSize(RESEARCHED);
	}

	@Test
	@DisplayName("상가업소번호가 2,355개 전부 다르다 — 같은 번호가 둘이면 한 곳이 조용히 사라진다")
	void storeNumbersAreUnique() {
		Path queue = queueFile();
		assumeTrue(queue != null && Files.isRegularFile(queue), "GABOLLE_RESEARCH_QUEUE 가 없다");

		List<SbizRow> rows = new ArrayList<>();
		ResearchQueueReader.read(queue, 500, rows::addAll);

		Set<String> ids = new HashSet<>();
		List<String> duplicated = rows.stream().map(SbizRow::storeId).filter(id -> !ids.add(id)).toList();

		// 적재기는 같은 번호를 건너뛰므로, 중복이 있으면 그 수만큼 장소가 덜 들어간다.
		assertThat(duplicated).isEmpty();
	}

	@Test
	@DisplayName("좌표가 전부 부산 안에 있다 — 한 곳이라도 밖이면 거리 점수가 그 가게를 엉뚱하게 놓는다")
	void everyCoordinateIsInsideBusan() {
		Path queue = queueFile();
		assumeTrue(queue != null && Files.isRegularFile(queue), "GABOLLE_RESEARCH_QUEUE 가 없다");

		List<SbizRow> rows = new ArrayList<>();
		ResearchQueueReader.read(queue, 500, rows::addAll);

		// 부산 경계를 넉넉히 감싼 상자. 정확한 경계 판정이 아니라 "완전히 엉뚱한 값" 을 잡는 그물이다.
		List<SbizRow> outside = rows.stream()
				.filter(r -> r.lat() < 34.8 || r.lat() > 35.5 || r.lng() < 128.7 || r.lng() > 129.4)
				.toList();

		assertThat(outside).isEmpty();
	}
}
