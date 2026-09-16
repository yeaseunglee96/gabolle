package com.gabolle.backend.weather.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gabolle.backend.weather.config.WeatherProperties;
import com.gabolle.backend.weather.domain.KmaGridCoordinate;

/**
 * 부산 격자의 예보를 미리 받아 캐시에 채운다 — S15P21E201-993.
 *
 * <h2>왜 미리 받나</h2>
 *
 * 전에는 <b>요청이 올 때</b> 기상청을 불렀다. 그래서 호출량이 사용자 수·좌표 수에 비례했고,
 * 그것을 막으려고 {@code GET /api/v1/weather} 가 로그인을 요구했다(그 주석이 이유를 적어
 * 뒀다 — "우리 키로 남이 대신 호출을 돌리는 것"). 그 문턱 때문에 비로그인 사용자에게 홈
 * 날씨 줄을 못 보여줬다.
 *
 * <p>미리 받아 두면 <b>호출량이 사용자와 무관하게 고정</b>된다. 요청 경로에서 기상청이
 * 빠지므로 문턱의 이유가 사라지고, 익명에게 열어도 한도를 태울 방법이 없다.
 *
 * <h2>얼마나 부르나 — 실측 (2026-09-15)</h2>
 *
 * 부산 장소 53,716곳의 실제 좌표를 {@code KmaGridConverter} 로 변환해 세었다.
 *
 * <pre>
 * 서로 다른 격자 : 51칸 (5km 간격)
 * 발표 회차      : 하루 8회
 * 하루 호출량    : 51 × 8 = 408회
 * </pre>
 *
 * <p>🔴 <b>기상청 키의 일일 한도는 아직 모른다.</b> 408회가 그 안인지 확인되지 않았다 —
 * 넘으면 {@code gabolle.weather.prefetch.enabled=false} 로 끄거나 범위를 줄인다. 코드를
 * 고칠 필요는 없다.
 *
 * <h2>실패를 조용히 넘기지 않는다</h2>
 *
 * 한 칸이 실패해도 나머지는 계속 받는다 — 한 칸 때문에 부산 전체가 비면 안 된다. 다만 몇
 * 칸을 채웠고 몇 칸이 실패했는지 <b>매번 로그에 남긴다.</b> 전부 실패했으면 ERROR 다(키가
 * 죽었거나 기상청이 내려간 것이라, 그 사실이 조용하면 사람이 못 알아챈다).
 */
@Component
@Profile({ "db", "dev" })
public class WeatherPrefetchScheduler {

	private static final Logger log = LoggerFactory.getLogger(WeatherPrefetchScheduler.class);

	private final WeatherService weatherService;

	private final WeatherProperties properties;

	public WeatherPrefetchScheduler(WeatherService weatherService, WeatherProperties properties) {
		this.weatherService = weatherService;
		this.properties = properties;
	}

	@Scheduled(cron = "${gabolle.weather.prefetch.cron:0 5 * * * *}", zone = "Asia/Seoul")
	public void prefetchBusan() {
		WeatherProperties.Prefetch config = this.properties.getPrefetch();
		if (!config.isEnabled()) {
			return;
		}
		run();
	}

	/**
	 * 한 바퀴 돈다. 스케줄과 분리해 둔 이유는 <b>테스트가 시간을 기다리지 않게</b> 하기
	 * 위해서다.
	 *
	 * @return 이번에 새로 채운 칸 수
	 */
	public int run() {
		WeatherProperties.Prefetch config = this.properties.getPrefetch();
		List<KmaGridCoordinate> grids = BusanGridEnumerator.enumerate(config.getLatMin(), config.getLatMax(),
				config.getLonMin(), config.getLonMax());

		int fetched = 0;
		int alreadyHad = 0;
		int failed = 0;
		for (KmaGridCoordinate grid : grids) {
			try {
				if (this.weatherService.prefetch(grid)) {
					fetched++;
				}
				else {
					alreadyHad++;
				}
			}
			catch (RuntimeException ex) {
				failed++;
				log.warn("날씨 미리받기 실패 — 격자 {},{} : {}", grid.nx(), grid.ny(), ex.getMessage());
			}
		}

		if (failed == grids.size() && !grids.isEmpty()) {
			log.error("날씨 미리받기가 {}칸 전부 실패했습니다. 기상청 키나 바깥 서비스를 확인하세요.", grids.size());
		}
		else {
			log.info("날씨 미리받기 — 격자 {}칸 중 새로 {} · 이미 있음 {} · 실패 {}", grids.size(), fetched, alreadyHad,
					failed);
		}
		return fetched;
	}

}
