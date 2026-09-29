package com.gabolle.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.route.application.RouteCache;
import com.gabolle.backend.route.config.RouteProperties;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

class RouteCacheTest {

	private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");

	private static RouteQuery query(double originLat, double originLng, TravelMode mode) {
		return new RouteQuery(originLat, originLng, 35.1796, 129.0756, mode);
	}

	private static RouteLeg realLeg() {
		return new RouteLeg(TravelMode.CAR, 11132, 44, 15700, 0, null, false, null,
				RouteLeg.PROVIDER_KAKAO_MOBILITY, List.of(), List.of());
	}

	private static RouteLeg estimatedLeg() {
		return new RouteLeg(TravelMode.CAR, 10000, 24, null, null, null, true, "업체 실패",
				RouteLeg.PROVIDER_STRAIGHT_LINE, List.of(), List.of());
	}

	@Test
	@DisplayName("🔴 계단을 피하는 길은 보통 길과 다른 열쇠다 — 같이 쓰면 계단 길이 휠체어 사용자에게 나간다")
	void stepFreeHasItsOwnEntry() {
		RouteCache cache = new RouteCache(new RouteProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
		RouteQuery plain = new RouteQuery(35.1587, 129.1604, 35.1796, 129.0756, TravelMode.WALK);
		RouteQuery stepFree = new RouteQuery(35.1587, 129.1604, 35.1796, 129.0756, TravelMode.WALK, null, true);

		cache.put(plain, realLeg());

		assertThat(cache.find(stepFree)).isEmpty();
		assertThat(cache.find(plain)).isPresent();
	}

	@Test
	@DisplayName("담아 두면 같은 요청에 그대로 돌려준다")
	void returnsWhatWasStored() {
		RouteCache cache = new RouteCache(new RouteProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
		RouteQuery q = query(35.1587, 129.1604, TravelMode.CAR);

		cache.put(q, realLeg());

		assertThat(cache.find(q)).contains(realLeg());
	}

	@Test
	@DisplayName("🔴 손가락 하나만큼 어긋난 좌표도 같은 경로로 본다 — 안 그러면 캐시가 한 번도 안 맞는다")
	void nearlyIdenticalCoordinatesShareOneEntry() {
		RouteCache cache = new RouteCache(new RouteProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
		cache.put(query(35.1587, 129.1604, TravelMode.CAR), realLeg());

		// 위도 0.00001° 는 약 1m 다. 사람에게 같은 자리다.
		assertThat(cache.find(query(35.15871, 129.16041, TravelMode.CAR))).isPresent();
	}

	@Test
	@DisplayName("이동수단이 다르면 다른 경로다 — 같은 좌표라도 자차와 도보는 답이 다르다")
	void modeIsPartOfTheKey() {
		RouteCache cache = new RouteCache(new RouteProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
		cache.put(query(35.1587, 129.1604, TravelMode.CAR), realLeg());

		assertThat(cache.find(query(35.1587, 129.1604, TravelMode.WALK))).isEmpty();
	}

	@Test
	@DisplayName("🔴 추정은 담지 않는다 — 실패를 오래 기억하는 캐시는 장애를 늘린다")
	void doesNotStoreEstimates() {
		RouteCache cache = new RouteCache(new RouteProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
		RouteQuery q = query(35.1587, 129.1604, TravelMode.CAR);

		cache.put(q, estimatedLeg());

		assertThat(cache.find(q)).isEmpty();
	}

	@Test
	@DisplayName("시간이 지나면 버린다")
	void expiresAfterTtl() {
		MutableClock clock = new MutableClock(NOW);
		RouteProperties properties = new RouteProperties();
		properties.setCacheTtl(Duration.ofMinutes(10));
		RouteCache cache = new RouteCache(properties, clock);
		RouteQuery q = query(35.1587, 129.1604, TravelMode.CAR);

		cache.put(q, realLeg());
		clock.advance(Duration.ofMinutes(11));

		assertThat(cache.find(q)).isEmpty();
	}

	@Test
	@DisplayName("🔴 상한을 넘으면 가장 오래 안 쓴 것부터 버린다 — 넣은 순서로 버리면 자주 쓰는 경로가 밀려난다")
	void evictsLeastRecentlyUsed() {
		RouteProperties properties = new RouteProperties();
		properties.setCacheMaxEntries(2);
		RouteCache cache = new RouteCache(properties, Clock.fixed(NOW, ZoneOffset.UTC));

		RouteQuery first = query(35.1000, 129.1000, TravelMode.CAR);
		RouteQuery second = query(35.2000, 129.2000, TravelMode.CAR);
		RouteQuery third = query(35.3000, 129.3000, TravelMode.CAR);

		cache.put(first, realLeg());
		cache.put(second, realLeg());
		cache.find(first); // first 를 다시 써서 "최근에 쓴 것" 으로 만든다
		cache.put(third, realLeg());

		assertThat(cache.find(first)).as("최근에 쓴 것은 남는다").isPresent();
		assertThat(cache.find(second)).as("가장 오래 안 쓴 것이 밀려난다").isEmpty();
		assertThat(cache.find(third)).isPresent();
	}

	/** 앞으로만 가는 시계. */
	private static final class MutableClock extends Clock {

		private Instant now;

		private MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			this.now = this.now.plus(duration);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now;
		}
	}
}
