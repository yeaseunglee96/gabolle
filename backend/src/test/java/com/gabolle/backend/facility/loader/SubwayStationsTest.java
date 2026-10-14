package com.gabolle.backend.facility.loader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.facility.loader.SubwayStations.Station;

/**
 * 역 자료에서 역을 찾는 규칙 (S15P21E201-2052) — 실제 {@code transit/busan-subway-network.json} 으로 본다. 물품보관함 파일의 역 이름이
 * 역 자료와 다르게 적힌 곳(「서면」「벡스코」「국제금융센터」)을 못 찾으면 그 줄이 통째로 빠진다.
 */
class SubwayStationsTest {

	private final SubwayStations stations = SubwayStations.fromClasspath();

	@Test
	@DisplayName("이름과 호선으로 찾는다 — 「역」 글자와 「(1호선)」 표시는 비교에서 뗀다")
	void findsByNameAndLine() {
		assertThat(this.stations.find("하단", "1")).get().extracting(Station::stopId).isEqualTo("102");
		assertThat(this.stations.find("하단역", "1")).get().extracting(Station::stopId).isEqualTo("102");
		assertThat(this.stations.find("다대포해수욕장", "1")).get().extracting(Station::stopId).isEqualTo("95");
		assertThat(this.stations.find("부산", "1")).get().extracting(Station::stopId).isEqualTo("113");
	}

	@Test
	@DisplayName("🔴 환승역은 호선마다 다른 역이다 — 서면 1호선과 2호선, 동래 1호선과 4호선은 좌표가 다르다")
	void transferStationsAreSeparatePerLine() {
		Station seomyeon1 = this.stations.find("서면", "1").orElseThrow();
		Station seomyeon2 = this.stations.find("서면", "2").orElseThrow();
		assertThat(seomyeon1.stopId()).isEqualTo("119");
		assertThat(seomyeon2.stopId()).isEqualTo("219");
		assertThat(seomyeon1.name()).isEqualTo("서면역(1호선)");

		assertThat(this.stations.find("동래", "1")).get().extracting(Station::stopId).isEqualTo("125");
		assertThat(this.stations.find("동래", "4")).get().extracting(Station::stopId).isEqualTo("402");
		assertThat(this.stations.find("수영", "2")).get().extracting(Station::stopId).isEqualTo("208");
		assertThat(this.stations.find("수영", "3")).get().extracting(Station::stopId).isEqualTo("301");
		assertThat(this.stations.find("덕천", "3")).get().extracting(Station::stopId).isEqualTo("313");
	}

	@Test
	@DisplayName("🔴 물품보관함 파일이 다르게 적은 이름 둘 — 「벡스코」는 시립미술관역, 「국제금융센터」는 국제금융센터부산은행역")
	void aliasesForNamesTheFileSpellsDifferently() {
		assertThat(this.stations.find("벡스코", "2")).get().extracting(Station::stopId).isEqualTo("205");
		assertThat(this.stations.find("국제금융센터", "2")).get().extracting(Station::stopId).isEqualTo("217");
	}

	@Test
	@DisplayName("모르는 역이나 호선이 맞지 않으면 비어 있다 — 가장 비슷한 역으로 대충 맞추지 않는다")
	void unknownStationOrWrongLineIsEmpty() {
		assertThat(this.stations.find("없는역", "1")).isEmpty();
		assertThat(this.stations.find("하단", "2")).isEmpty();
		assertThat(this.stations.find("하단", "")).isEmpty();
		assertThat(this.stations.find(null, "1")).isEmpty();
		assertThat(this.stations.find("하단", null)).isEmpty();
	}

	@Test
	@DisplayName("정류장 번호로 찾는다 — 환승주차장 번호(METRO-102-0)의 가운데 숫자")
	void findsByStopId() {
		Station hadan = this.stations.byStopId("102").orElseThrow();
		assertThat(hadan.name()).isEqualTo("하단역");
		assertThat(hadan.line()).isEqualTo("1");
		assertThat(hadan.lat()).isBetween(35.10, 35.11);
		assertThat(this.stations.byStopId("999999")).isEmpty();
	}

	@Test
	@DisplayName("이름 맞추기 규칙: 끝의 「역」과 「(N호선)」, 공백을 뗀다")
	void normalizeStripsStationMarkers() {
		assertThat(SubwayStations.normalize("서면역(1호선)")).isEqualTo("서면");
		assertThat(SubwayStations.normalize("덕천역(2호선)")).isEqualTo("덕천");
		assertThat(SubwayStations.normalize("경성대부경대역")).isEqualTo("경성대부경대");
		assertThat(SubwayStations.normalize(" 부산 ")).isEqualTo("부산");
		assertThat(SubwayStations.normalize("역")).isEqualTo("역");
	}
}
