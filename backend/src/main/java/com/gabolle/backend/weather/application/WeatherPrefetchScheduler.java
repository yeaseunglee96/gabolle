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
 * 부산 격자의 예보를 미리 받아 캐시에 채운다. 요청 경로에서 기상청이 빠지므로 호출량이
 * 사용자 수와 무관하게 고정되고, 그래서 익명에게 열어도 한도를 태울 방법이 없다.
 * 부산 격자는 51칸이라 하루 51 × 8회 발표 = 408회를 부른다.
 *
 * 한 칸이 실패해도 나머지는 계속 받는다. 전부 실패하면 ERROR 로 남긴다 — 키가 죽었거나
 * 기상청이 내려간 것이고, 조용하면 사람이 못 알아챈다.
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

	/** 한 바퀴 돌고 새로 채운 칸 수를 돌려준다. 스케줄과 나눠 둬야 테스트가 시간을 안 기다린다. */
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
