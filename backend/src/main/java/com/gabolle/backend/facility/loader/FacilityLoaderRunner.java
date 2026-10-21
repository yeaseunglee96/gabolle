package com.gabolle.backend.facility.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.facility.domain.FacilityKind;
import com.gabolle.backend.facility.repository.FacilityRepository;

/**
 * 근처 편의시설 NDJSON 넷을 한 번 읽어 {@code facility} 에 넣는다 (S15P21E201-2052).
 *
 * <pre>
 * java -jar gabolle-backend.jar \
 *   --spring.profiles.active=dev \
 *   --gabolle.facility.loader.parking=/load/facilities-20261014/parking.ndjson \
 *   --gabolle.facility.loader.pharmacy=/load/facilities-20261014/pharmacy.ndjson \
 *   --gabolle.facility.loader.toilet=/load/facilities-20261014/toilet.ndjson \
 *   --gabolle.facility.loader.locker=/load/facilities-20261014/locker.ndjson \
 *   --gabolle.facility.loader.dataset-version=facilities-20261014
 * </pre>
 *
 * <p>파일 넷을 다 주는 것이 표준이다. <b>하나만 줘도 돈다</b> — 약국처럼 자주 바뀌는 자료만 다시 넣을 때 쓴다. 주지 않은 종류는 손대지
 * 않는다. 네 프로퍼티가 모두 없으면 이 빈은 안 올라간다. 수집분 버전({@code dataset-version})은 늘 필수다.
 *
 * <p>🔴 <b>파일을 전부 먼저 검사한다.</b> 파일이 없거나, 수집분 버전이 없거나, 한 줄이라도 틀리면(모르는 칸·틀린 모양) 기동을 세우고 한 행도
 * 안 넣는다. 읽고도 안 넣는 줄(주소가 구까지뿐인 화장실·역을 못 찾은 물품보관함)과 비운 값은 멈추지 않고 <b>로그에 남긴다.</b>
 *
 * <p>같은 파일을 다시 돌려도 행이 안 늘고 값이 같으면 DB 도 안 건드린다. 새 수집분에서 사라진 행은 <b>지우지 않고</b> 그대로 두며 로그에
 * 몇 개인지만 센다. 마침 줄에 「적재를 마쳤다」가 들어 있어야 운영 절차의 {@code run} 함수(백엔드 {@code docs/PLACE-DATA-LOAD.md} 3절)가
 * 컨테이너를 스스로 내린다 — 그래서 <b>다른 줄에는 이 말을 쓰지 않는다.</b>
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnExpression("'${gabolle.facility.loader.parking:}${gabolle.facility.loader.pharmacy:}"
		+ "${gabolle.facility.loader.toilet:}${gabolle.facility.loader.locker:}' != ''")
public class FacilityLoaderRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(FacilityLoaderRunner.class);

	private static final int CHUNK = 500;

	private final FacilityLoader loader;

	private final FacilityRepository repository;

	private final String parkingPath;

	private final String pharmacyPath;

	private final String toiletPath;

	private final String lockerPath;

	private final String datasetVersion;

	public FacilityLoaderRunner(FacilityLoader loader, FacilityRepository repository,
			@Value("${gabolle.facility.loader.parking:}") String parkingPath,
			@Value("${gabolle.facility.loader.pharmacy:}") String pharmacyPath,
			@Value("${gabolle.facility.loader.toilet:}") String toiletPath,
			@Value("${gabolle.facility.loader.locker:}") String lockerPath,
			@Value("${gabolle.facility.loader.dataset-version:}") String datasetVersion) {
		this.loader = loader;
		this.repository = repository;
		this.parkingPath = parkingPath;
		this.pharmacyPath = pharmacyPath;
		this.toiletPath = toiletPath;
		this.lockerPath = lockerPath;
		this.datasetVersion = datasetVersion;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.datasetVersion == null || this.datasetVersion.isBlank()) {
			throw new IllegalStateException(
					"gabolle.facility.loader.dataset-version 을 함께 줘야 한다 — 어느 수집분인지 적지 않으면 "
							+ "이 편의시설을 나중에 되돌릴 수 없다");
		}
		Path parking = existingFile(this.parkingPath, "주차장");
		Path pharmacy = existingFile(this.pharmacyPath, "약국");
		Path toilet = existingFile(this.toiletPath, "화장실");
		Path locker = existingFile(this.lockerPath, "물품보관함");

		OffsetDateTime collectedAt = OffsetDateTime.now();
		long startedAt = System.nanoTime();
		LOGGER.info("근처 편의시설 적재를 시작한다 — 수집분={} · 주차장={} · 약국={} · 화장실={} · 물품보관함={}", this.datasetVersion, parking,
				pharmacy, toilet, locker);

		// 파일 전체를 먼저 검사한다 — 틀린 줄이 있으면 여기서 멈추고 한 행도 안 들어간다.
		SubwayStations stations = parking != null || locker != null ? SubwayStations.fromClasspath() : null;
		List<FacilityReader.Result> reads = new ArrayList<>();
		if (parking != null) {
			reads.add(FacilityReader.readParking(parking, stations));
		}
		if (pharmacy != null) {
			reads.add(FacilityReader.readPharmacy(pharmacy));
		}
		if (toilet != null) {
			reads.add(FacilityReader.readToilet(toilet));
		}
		if (locker != null) {
			reads.add(FacilityReader.readLocker(locker, stations));
		}
		reads.forEach(FacilityLoaderRunner::logRead);

		StringBuilder summary = new StringBuilder();
		for (FacilityReader.Result read : reads) {
			FacilityLoader.Result saved = save(read, collectedAt);
			summary.append(" · ").append(label(read.kind())).append(' ').append(read.rows().size()).append("곳(")
					.append(saved).append(')');
		}
		long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
		// 마침 줄 — 운영 절차의 run() 이 이 말에서 끊는다. 다른 줄에는 이 말을 쓰지 않는다.
		LOGGER.info("근처 편의시설 적재를 마쳤다 — 수집분 {}{} · {}ms", this.datasetVersion, summary, elapsedMs);
	}

	/** 읽은 결과 — 무엇을 왜 안 넣었고 무엇을 비웠는지 로그만 보고 알 수 있어야 한다. */
	private static void logRead(FacilityReader.Result read) {
		for (FacilityReader.Skipped skipped : read.skipped()) {
			LOGGER.warn("안 넣고 건너뜀({}·{}) — {}", label(read.kind()), skipped.reason(), skipped);
		}
		LOGGER.info("{} 읽음 — 읽은 줄 {} · 넣을 곳 {} · 건너뜀 {}(주소가 구까지뿐 {} · 역을 못 찾음 {}) · 비운 값 {}", label(read.kind()),
				read.linesRead(), read.rows().size(), read.skipped().size(),
				read.skippedCount(FacilityReader.Reason.COARSE_ADDRESS),
				read.skippedCount(FacilityReader.Reason.STATION_NOT_FOUND), read.nulled());
		if (read.positionFixed() > 0) {
			LOGGER.info("{} 위치를 부산교통공사 역 좌표로 바로잡음 — {}곳(파일 좌표와 {}m 넘게 어긋난 곳 {} · 가장 멀리 {}m)", label(read.kind()),
					read.positionFixed(), FacilityReader.FAR_METERS, read.farMoved(), read.maxMovedMeters());
		}
	}

	private FacilityLoader.Result save(FacilityReader.Result read, OffsetDateTime collectedAt) {
		FacilityKind kind = read.kind();
		long before = this.repository.countByKind(kind);
		List<FacilityRow> rows = read.rows();
		FacilityLoader.Result total = FacilityLoader.Result.empty();
		for (int from = 0; from < rows.size(); from += CHUNK) {
			total = total.plus(this.loader.saveChunk(rows.subList(from, Math.min(from + CHUNK, rows.size())),
					this.datasetVersion, collectedAt));
		}
		// 파일에 없는데 DB 에는 있는 행 — 지우지 않는다. 같은 파일을 다시 돌리면 0 이어야 한다.
		long vanished = Math.max(0, before - total.alreadyThere());
		LOGGER.info("{} 넣음 — {} · 파일에 없는 기존 행 {}개(지우지 않고 그대로 둠) · DB 의 {} 행 수 {} → {}", label(kind), total, vanished,
				label(kind), before, this.repository.countByKind(kind));
		return total;
	}

	private static Path existingFile(String configured, String what) {
		if (configured == null || configured.isBlank()) {
			return null;
		}
		Path file = Path.of(configured);
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException(what + " NDJSON 을 찾을 수 없다: " + file.toAbsolutePath());
		}
		return file;
	}

	private static String label(FacilityKind kind) {
		return Map.of(FacilityKind.PARKING, "주차장", FacilityKind.PHARMACY, "약국", FacilityKind.TOILET, "화장실",
				FacilityKind.LOCKER, "물품보관함").get(kind);
	}
}
