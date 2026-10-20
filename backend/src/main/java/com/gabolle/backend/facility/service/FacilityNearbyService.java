package com.gabolle.backend.facility.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.gabolle.backend.facility.api.FacilityDetail;
import com.gabolle.backend.facility.api.FacilityNearbyResponse;
import com.gabolle.backend.facility.api.FacilityRequestException;
import com.gabolle.backend.facility.domain.Facility;
import com.gabolle.backend.facility.domain.FacilityKind;
import com.gabolle.backend.facility.domain.ParkingFee;
import com.gabolle.backend.facility.domain.WeeklyHours;
import com.gabolle.backend.facility.repository.FacilityRepository;
import com.gabolle.backend.place.service.GeoDistance;

/**
 * 내 위치(또는 고른 목적지) 근처의 주차장·약국·공중화장실·물품보관함 (S15P21E201-2052).
 *
 * <p>좌표와 종류만으로 답이 정해진다 — 사용자별로 다른 것이 없어 요청자를 받지 않는다. 거리는 직선(하버사인)이다. 경계상자로 후보를 좁혀 받은
 * 뒤({@link FacilityRepository#findInBox}) 실제 거리로 다시 거른다 — 상자는 반경보다 넓어 바깥 후보가 섞여 들어온다.
 *
 * <h2>정렬</h2>
 * <ul>
 *   <li><b>주차장</b> — 예상요금이 싼 순, 같으면 가까운 순. 🔴 <b>요금을 모르는 곳(예상요금 null)은 요금을 아는 곳 뒤로 보낸다.</b>
 *       절대 0원으로 세지 않는다 — 그러면 요금을 모르는 곳이 가장 싼 곳으로 맨 위에 뜬다. 무료 주차장은 0원이라 맨 앞이다</li>
 *   <li><b>약국</b> — 지금 연 곳이 먼저, 그다음 가까운 순</li>
 *   <li><b>화장실·물품보관함</b> — 가까운 순</li>
 * </ul>
 *
 * <h2>🔴 공휴일</h2>
 * 이 프로젝트에는 공휴일 달력이 없다. 그래서 「오늘이 공휴일인가」는 늘 아니라고 답하고({@link #isPublicHoliday}) 요일 칸으로만
 * 판정한다. 평일에 낀 공휴일에는 평일 시간으로 «연다»고 나올 수 있다 — 화면이 「방문 전 전화로 확인」을 함께 적는 이유다. 달력이
 * 생기면 {@link #isPublicHoliday} 한 곳만 고친다.
 */
@Service
@Profile({ "db", "dev" })
public class FacilityNearbyService {

	public static final int DEFAULT_RADIUS_M = 700;

	public static final int MAX_RADIUS_M = 2000;

	public static final int DEFAULT_LIMIT = 20;

	public static final int MAX_LIMIT = 50;

	public static final int DEFAULT_STAY_MINUTES = 60;

	/** 반경 안의 후보를 한 번에 받는 상한. 반경 2km 의 화장실이 많아야 수백 곳이라 넉넉하다. */
	private static final int SCAN_LIMIT = 3000;

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	/** 밤 11시. 어느 날이든 이 시각 이후까지 여는 약국을 심야로 본다. */
	private static final int LATE_NIGHT_MINUTE = 23 * 60;

	private final FacilityRepository repository;

	private final Clock clock;

	/** 환전소 — 우리 표가 아니라 바깥에 묻는다. 없으면(시험) 환전소는 늘 빈 목록이다. */
	private final ExchangeSearch exchangeSearch;

	public FacilityNearbyService(FacilityRepository repository, Clock clock) {
		this(repository, clock, null);
	}

	@Autowired
	public FacilityNearbyService(FacilityRepository repository, Clock clock, ExchangeSearch exchangeSearch) {
		this.repository = repository;
		this.clock = clock;
		this.exchangeSearch = exchangeSearch;
	}

	/**
	 * 요청. 숫자 칸이 비어 있으면(null) 기본값을 쓴다.
	 *
	 * @param kind 종류 글자 — 대소문자를 가리지 않는다
	 * @param accessible 화장실만 — {@code true} 면 장애인용만. {@code false}·null 이면 거르지 않는다
	 * @param diaper 화장실만 — {@code true} 면 기저귀 교환대가 있는 곳만
	 */
	public record Query(String kind, double lat, double lng, Integer radiusM, Integer limit, Integer stayMinutes,
			Boolean accessible, Boolean diaper) {
	}

	/** @throws FacilityRequestException 값이 범위 밖이거나 종류를 모를 때 */
	public FacilityNearbyResponse nearby(Query query) {
		Validated request = validate(query);
		if (request.kind() == FacilityKind.EXCHANGE) {
			return exchangeNearby(request);
		}
		GeoDistance.BoundingBox box = GeoDistance.boundingBox(request.lat(), request.lng(), request.radiusM());
		List<Facility> found = this.repository.findInBox(request.kind(), box.minLat(), box.maxLat(), box.minLng(),
				box.maxLng(), Limit.of(SCAN_LIMIT));

		ZonedDateTime now = ZonedDateTime.now(this.clock).withZoneSameInstant(SEOUL);
		LocalDateTime local = now.toLocalDateTime();
		boolean holiday = isPublicHoliday(local.toLocalDate());

		List<Ranked> ranked = new ArrayList<>();
		for (Facility facility : found) {
			double meters = GeoDistance.meters(request.lat(), request.lng(), facility.getLat(), facility.getLng());
			if (meters > request.radiusM()) {
				continue;
			}
			FacilityDetail detail = detailOf(facility, request.stayMinutes(), local, holiday);
			if (!passesFilters(detail, request)) {
				continue;
			}
			ranked.add(new Ranked(facility, meters, detail));
		}
		ranked.sort(comparatorOf(request.kind()));

		List<FacilityNearbyResponse.Item> items = ranked.stream().limit(request.limit())
				.map(each -> new FacilityNearbyResponse.Item(each.facility().getFacilityId().toString(),
						each.facility().getKind().name(), each.facility().getName(), each.facility().getAddress(),
						each.facility().getLat(), each.facility().getLng(), (int) Math.round(each.meters()), each.detail()))
				.toList();
		return new FacilityNearbyResponse(request.kind().name(), request.lat(), request.lng(), request.radiusM(),
				request.kind() == FacilityKind.PARKING ? request.stayMinutes() : null, ranked.size(), request.limit(),
				now.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), items);
	}

	/**
	 * 환전소 — 카카오 로컬이 가까운 순으로 준 것을 그대로. 바깥 호출이 실패하면 빈 목록으로 답한다 — 이 화면의 다른 탭까지 깨지 않게.
	 * 거리는 우리 하버사인으로 다시 잰다(다른 종류와 같은 잣대).
	 */
	private FacilityNearbyResponse exchangeNearby(Validated request) {
		ZonedDateTime now = ZonedDateTime.now(this.clock).withZoneSameInstant(SEOUL);
		List<ExchangeSearch.Spot> spots = List.of();
		if (this.exchangeSearch != null) {
			try {
				spots = this.exchangeSearch.search(request.lat(), request.lng(), request.radiusM(), request.limit());
			} catch (IllegalStateException exception) {
				spots = List.of();
			}
		}
		List<FacilityNearbyResponse.Item> items = new ArrayList<>();
		for (ExchangeSearch.Spot spot : spots) {
			double meters = GeoDistance.meters(request.lat(), request.lng(), spot.lat(), spot.lng());
			if (meters > request.radiusM()) {
				continue;
			}
			items.add(new FacilityNearbyResponse.Item("kakao:" + spot.id(), FacilityKind.EXCHANGE.name(), spot.name(),
					spot.address(), spot.lat(), spot.lng(), (int) Math.round(meters),
					new FacilityDetail.Exchange(spot.tel(), spot.placeUrl())));
		}
		items.sort(Comparator.comparingInt(FacilityNearbyResponse.Item::distanceM));
		List<FacilityNearbyResponse.Item> limited = items.stream().limit(request.limit()).toList();
		return new FacilityNearbyResponse(FacilityKind.EXCHANGE.name(), request.lat(), request.lng(), request.radiusM(),
				null, items.size(), request.limit(), now.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
				limited);
	}

	// ── 요청 검사 ───────────────────────────────────────────────────────────

	private record Validated(FacilityKind kind, double lat, double lng, int radiusM, int limit, int stayMinutes,
			boolean accessibleOnly, boolean diaperOnly) {
	}

	private static Validated validate(Query query) {
		List<String> invalid = new ArrayList<>();
		FacilityKind kind = FacilityKind.fromName(query.kind()).orElse(null);
		if (kind == null) {
			invalid.add("kind");
		}
		if (!Double.isFinite(query.lat()) || query.lat() < -90 || query.lat() > 90) {
			invalid.add("lat");
		}
		if (!Double.isFinite(query.lng()) || query.lng() < -180 || query.lng() > 180) {
			invalid.add("lng");
		}
		int radius = query.radiusM() == null ? DEFAULT_RADIUS_M : query.radiusM();
		if (radius < 1 || radius > MAX_RADIUS_M) {
			invalid.add("radiusM");
		}
		int limit = query.limit() == null ? DEFAULT_LIMIT : query.limit();
		if (limit < 1 || limit > MAX_LIMIT) {
			invalid.add("limit");
		}
		int stay = query.stayMinutes() == null ? DEFAULT_STAY_MINUTES : query.stayMinutes();
		if (stay < 1 || stay > ParkingFee.MAX_STAY_MINUTES) {
			invalid.add("stayMinutes");
		}
		if (!invalid.isEmpty()) {
			throw new FacilityRequestException("요청 값이 올바르지 않습니다.", invalid);
		}
		return new Validated(kind, query.lat(), query.lng(), radius, limit, stay, Boolean.TRUE.equals(query.accessible()),
				Boolean.TRUE.equals(query.diaper()));
	}

	private static boolean passesFilters(FacilityDetail detail, Validated request) {
		if (detail instanceof FacilityDetail.Toilet toilet) {
			return (!request.accessibleOnly() || toilet.accessible()) && (!request.diaperOnly() || toilet.diaper());
		}
		return true;
	}

	// ── 정렬 ────────────────────────────────────────────────────────────────

	private record Ranked(Facility facility, double meters, FacilityDetail detail) {
	}

	private static Comparator<Ranked> comparatorOf(FacilityKind kind) {
		Comparator<Ranked> byDistance = Comparator.comparingDouble(Ranked::meters);
		Comparator<Ranked> primary = switch (kind) {
			// 요금을 아는 곳(무료 포함)이 먼저, 그 안에서 싼 순. 모르는 곳은 맨 뒤 — 0원으로 세지 않는다.
			case PARKING -> Comparator.<Ranked>comparingInt(each -> estimatedWon(each) == null ? 1 : 0)
					.thenComparingInt(each -> estimatedWon(each) == null ? 0 : estimatedWon(each));
			// 지금 연 곳이 먼저.
			case PHARMACY -> Comparator.comparingInt(each -> Boolean.TRUE.equals(openNow(each)) ? 0 : 1);
			case TOILET, LOCKER, EXCHANGE -> (left, right) -> 0;
		};
		// 같으면 가까운 순, 그래도 같으면 이름·번호로 못 박아 늘 같은 순서가 나오게 한다.
		return primary.thenComparing(byDistance).thenComparing(each -> each.facility().getName())
				.thenComparing(each -> each.facility().getFacilityId());
	}

	private static Integer estimatedWon(Ranked ranked) {
		return ranked.detail() instanceof FacilityDetail.Parking parking ? parking.estimatedWon() : null;
	}

	private static Boolean openNow(Ranked ranked) {
		return ranked.detail() instanceof FacilityDetail.Pharmacy pharmacy ? pharmacy.openNow() : null;
	}

	// ── 종류별 응답 칸 ──────────────────────────────────────────────────────

	private static FacilityDetail detailOf(Facility facility, int stayMinutes, LocalDateTime now, boolean holiday) {
		Map<String, Object> stored = facility.getDetail();
		return switch (facility.getKind()) {
			case PARKING -> parkingOf(stored, stayMinutes, now, holiday);
			case PHARMACY -> pharmacyOf(stored, now, holiday);
			case TOILET -> toiletOf(stored);
			case LOCKER -> lockerOf(stored);
			// 표에 환전소 행은 없다(바깥에 묻는다) — 혹시 있어도 저장된 칸만으로 그린다.
			case EXCHANGE -> new FacilityDetail.Exchange(text(stored, "tel"), text(stored, "placeUrl"));
		};
	}

	private static FacilityDetail.Parking parkingOf(Map<String, Object> stored, int stayMinutes, LocalDateTime now,
			boolean holiday) {
		boolean free = Boolean.TRUE.equals(stored.get("free"));
		// 무료면 요금표가 필요 없다. 유료인데 요금표를 모르면 null — 0원으로 바꾸지 않는다.
		ParkingFee fee = free ? null : ParkingFee.fromDetail(stored.get("fee"));
		Integer estimated = free ? Integer.valueOf(0) : (fee == null ? null : fee.estimate(stayMinutes));
		WeeklyHours.Status status = WeeklyHours.fromDetail(stored.get("hours")).at(now, holiday);
		return new FacilityDetail.Parking(text(stored, "operator"), free, integer(stored, "spaces"), fee, estimated,
				text(stored, "note"), status.openNow(), hoursOf(status), status.closesAtText(),
				status.today() != null && status.today().allDay());
	}

	private static FacilityDetail.Pharmacy pharmacyOf(Map<String, Object> stored, LocalDateTime now, boolean holiday) {
		WeeklyHours hours = WeeklyHours.fromDetail(stored.get("hours"));
		WeeklyHours.Status status = hours.at(now, holiday);
		return new FacilityDetail.Pharmacy(text(stored, "tel"), text(stored, "directions"), text(stored, "note"), true,
				status.openNow(), hoursOf(status), status.closesAtText(),
				status.today() != null && status.today().allDay(), hours.opensOn("Sunday"), hours.opensOn("Holiday"),
				hours.anyCloseAtOrAfter(LATE_NIGHT_MINUTE));
	}

	private static FacilityDetail.Toilet toiletOf(Map<String, Object> stored) {
		return new FacilityDetail.Toilet(text(stored, "type"), text(stored, "openHours"),
				Boolean.TRUE.equals(stored.get("alwaysOpen")), Boolean.TRUE.equals(stored.get("accessible")),
				Boolean.TRUE.equals(stored.get("diaper")), text(stored, "diaperPlace"),
				Boolean.TRUE.equals(stored.get("emergencyBell")), Boolean.TRUE.equals(stored.get("cctv")),
				text(stored, "tel"));
	}

	private static FacilityDetail.Locker lockerOf(Map<String, Object> stored) {
		return new FacilityDetail.Locker(text(stored, "station"), text(stored, "stationEn"), text(stored, "line"),
				text(stored, "location"), count(stored, "small"), count(stored, "medium"), count(stored, "large"),
				count(stored, "xlarge"), text(stored, "feeText"), text(stored, "operator"));
	}

	private static FacilityDetail.Hours hoursOf(WeeklyHours.Status status) {
		return status.today() == null ? null
				: new FacilityDetail.Hours(status.today().openText(), status.today().closeText());
	}

	private static String text(Map<String, Object> stored, String key) {
		return stored.get(key) instanceof String value && !value.isBlank() ? value : null;
	}

	private static Integer integer(Map<String, Object> stored, String key) {
		return stored.get(key) instanceof Number value ? Integer.valueOf(value.intValue()) : null;
	}

	private static int count(Map<String, Object> stored, String key) {
		Integer value = integer(stored, key);
		return value == null ? 0 : value;
	}

	/**
	 * 오늘이 공휴일인가. 🔴 이 프로젝트에는 공휴일 달력이 없어 늘 {@code false} 다 — 요일 칸으로만 판정한다. 법정 공휴일·대체공휴일
	 * 달력(공공데이터포털 특일 정보 등)이 생기면 이 한 곳만 고친다. 약국·주차장 자료의 {@code Holiday} 칸은 이미 저장돼 있다.
	 */
	boolean isPublicHoliday(LocalDate date) {
		return false;
	}

}
