package com.gabolle.backend.itinerary.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.domain.ItineraryLeg;

/**
 * 구간의 경사·계단 조각을 {@code itinerary_leg.pieces} JSON 으로 적고 읽는 규칙. DB 없이 본다 — 실제 PostgreSQL 한 바퀴는
 * {@code ItineraryLegPathPersistenceIntegrationTest} 가 본다.
 */
class LegPiecesJsonTest {

	private static final List<double[]> ROAD = List.of(new double[] { 129.1604, 35.1587 },
			new double[] { 129.1204, 35.1553 }, new double[] { 129.0756, 35.1796 });

	@Test
	@DisplayName("경로 API 의 pieces 와 같은 모양으로 적고 그대로 읽는다 — 모르는 경사는 null 로 남는다")
	void roundTrip() {
		List<ItineraryLeg.Piece> pieces = List.of(new ItineraryLeg.Piece(0, 1, 2.5, false),
				new ItineraryLeg.Piece(1, 2, null, true));

		String json = JpaItineraryRepository.encodePieces(pieces);

		assertThat(json).isEqualTo("[{\"from\":0,\"to\":1,\"slopePercent\":2.5,\"stairs\":false},"
				+ "{\"from\":1,\"to\":2,\"slopePercent\":null,\"stairs\":true}]");
		assertThat(JpaItineraryRepository.decodePieces(json, ROAD)).isEqualTo(pieces);
	}

	@Test
	@DisplayName("비었으면 null 로 적는다 — 빈 배열은 「잰 결과가 비었다」로 읽힌다")
	void emptyIsNull() {
		assertThat(JpaItineraryRepository.encodePieces(List.of())).isNull();
		assertThat(JpaItineraryRepository.encodePieces(null)).isNull();
	}

	@Test
	@DisplayName("NaN 경사는 JSON 이 아니므로 모름(null)으로 적는다 — INSERT 가 깨져 일정 생성이 죽지 않게")
	void nonFiniteSlopeBecomesNull() {
		String json = JpaItineraryRepository.encodePieces(List.of(new ItineraryLeg.Piece(0, 1, Double.NaN, false)));

		assertThat(json).contains("\"slopePercent\":null");
	}

	@Test
	@DisplayName("🔴 선형 밖을 가리키거나 깨진 조각은 통째로 버린다 — 틀린 자리를 칠하느니 안 칠한다")
	void rejectsBrokenPieces() {
		assertThat(JpaItineraryRepository.decodePieces(
				"[{\"from\":0,\"to\":5,\"slopePercent\":1.0,\"stairs\":false}]", ROAD)).isNull();
		assertThat(JpaItineraryRepository.decodePieces("[{\"from\":0}]", ROAD)).isNull();
		assertThat(JpaItineraryRepository.decodePieces("not json", ROAD)).isNull();
		assertThat(JpaItineraryRepository.decodePieces(
				"[{\"from\":0,\"to\":1,\"slopePercent\":1.0,\"stairs\":false}]", null)).as("선형이 없다").isNull();
	}
}
