package com.gabolle.backend.weather;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gabolle.backend.weather.application.BusanGridEnumerator;
import com.gabolle.backend.weather.application.WeatherPrefetchScheduler;
import com.gabolle.backend.weather.application.WeatherService;
import com.gabolle.backend.weather.application.WeatherVendorException;
import com.gabolle.backend.weather.application.WeatherVendorPort;
import com.gabolle.backend.weather.config.WeatherProperties;
import com.gabolle.backend.weather.domain.KmaGridConverter;
import com.gabolle.backend.weather.domain.KmaGridCoordinate;
import com.gabolle.backend.weather.domain.WeatherForecastCacheRepository;
import com.gabolle.backend.weather.domain.WeatherQuery;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 미리 받아 두기와 익명 경계 — S15P21E201-993.
 *
 * <p>DB 도 바깥 호출도 없다. 컨테이너 없이 돈다.
 */
class WeatherPrefetchTest {

	/** 부산 한가운데 근처. 범위 안이라는 것만 쓰면 되므로 정확한 지점일 필요는 없다. */
	private static final double BUSAN_LAT = 35.18;

	private static final double BUSAN_LON = 129.07;

	private WeatherVendorPort vendor;

	private WeatherForecastCacheRepository cache;

	private WeatherService service;

	private WeatherProperties properties;

	@BeforeEach
	void setUp() {
		this.vendor = Mockito.mock(WeatherVendorPort.class);
		this.cache = Mockito.mock(WeatherForecastCacheRepository.class);
		this.properties = new WeatherProperties();
		this.service = new WeatherService(this.vendor, this.cache, this.properties,
				Clock.fixed(Instant.parse("2026-09-15T05:00:00Z"), ZoneId.of("Asia/Seoul")), new ObjectMapper());
	}

	@Test
	@DisplayName("🔴 익명 요청은 캐시에 없으면 기상청을 부르지 않는다 — 이게 한도를 지키는 경계다")
	void anonymousRequestNeverCallsTheVendor() {
		Mockito.when(this.cache.findFreshForecastJson(Mockito.anyString(), Mockito.any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.service
				.getForecastFromCache(new WeatherQuery(BUSAN_LAT, BUSAN_LON, LocalDate.of(2026, 9, 15))))
				.isInstanceOf(WeatherService.ForecastNotPreparedException.class);

		Mockito.verify(this.vendor, Mockito.never()).fetchForecastJson(Mockito.anyInt(), Mockito.anyInt(),
				Mockito.any());
	}

	@Test
	@DisplayName("로그인한 사람의 요청은 캐시에 없으면 기상청을 부른다 — 지금까지와 같다")
	void signedInRequestStillCallsTheVendorOnMiss() {
		Mockito.when(this.cache.findFreshForecastJson(Mockito.anyString(), Mockito.any())).thenReturn(Optional.empty());
		Mockito.when(this.vendor.fetchForecastJson(Mockito.anyInt(), Mockito.anyInt(), Mockito.any()))
				.thenThrow(new WeatherVendorException("KMA_CALL_FAILED", "부러 실패", org.springframework.http.HttpStatus.BAD_GATEWAY));

		assertThatThrownBy(
				() -> this.service.getForecast(new WeatherQuery(BUSAN_LAT, BUSAN_LON, LocalDate.of(2026, 9, 15))))
				.isInstanceOf(WeatherVendorException.class);

		Mockito.verify(this.vendor).fetchForecastJson(Mockito.anyInt(), Mockito.anyInt(), Mockito.any());
	}

	@Test
	@DisplayName("이미 받아 둔 격자는 다시 부르지 않는다")
	void prefetchSkipsGridsAlreadyCached() {
		Mockito.when(this.cache.findFreshForecastJson(Mockito.anyString(), Mockito.any()))
				.thenReturn(Optional.of("{\"이미\":\"있음\"}"));

		boolean fetched = this.service.prefetch(KmaGridConverter.toGrid(BUSAN_LAT, BUSAN_LON));

		assertThat(fetched).isFalse();
		Mockito.verifyNoInteractions(this.vendor);
	}

	@Test
	@DisplayName("🔴 범위를 펼치면 부산이 51칸 안팎으로 나온다 — 실측(장소 53,716곳)과 같은 자릿수여야 한다")
	void busanRangeEnumeratesAboutFiftyGrids() {
		WeatherProperties.Prefetch config = this.properties.getPrefetch();

		List<KmaGridCoordinate> grids = BusanGridEnumerator.enumerate(config.getLatMin(), config.getLatMax(),
				config.getLonMin(), config.getLonMax());

		// 실측 51칸은 "장소가 있는 곳" 기준이고 설정 범위는 가장자리를 조금 넉넉히 잡았다.
		// 그래서 같아야 한다고 못 박지 않고, 자릿수가 어긋나면(수백 칸이 되면) 잡는다.
		assertThat(grids).hasSizeBetween(40, 120);
		assertThat(grids).doesNotHaveDuplicates();
		assertThat(grids).contains(KmaGridConverter.toGrid(BUSAN_LAT, BUSAN_LON));
	}

	@Test
	@DisplayName("한 칸이 실패해도 나머지는 계속 받는다")
	void oneFailingGridDoesNotStopTheRest() {
		Mockito.when(this.cache.findFreshForecastJson(Mockito.anyString(), Mockito.any())).thenReturn(Optional.empty());
		Mockito.when(this.vendor.fetchForecastJson(Mockito.anyInt(), Mockito.anyInt(), Mockito.any()))
				.thenThrow(new WeatherVendorException("KMA_CALL_FAILED", "부러 실패", org.springframework.http.HttpStatus.BAD_GATEWAY))
				.thenReturn("{\"ok\":true}");

		int fetched = new WeatherPrefetchScheduler(this.service, this.properties).run();

		// 첫 칸은 실패하고 나머지는 받아 온다 — 예외가 밖으로 새지 않는다.
		assertThat(fetched).isGreaterThan(0);
	}

	@Test
	@DisplayName("꺼 두면 아무것도 안 부른다")
	void disabledPrefetchCallsNothing() {
		this.properties.getPrefetch().setEnabled(false);

		new WeatherPrefetchScheduler(this.service, this.properties).prefetchBusan();

		Mockito.verifyNoInteractions(this.vendor);
	}

}
