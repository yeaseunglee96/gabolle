package com.gabolle.backend.facility.loader;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.facility.domain.Facility;
import com.gabolle.backend.facility.domain.FacilityKind;
import com.gabolle.backend.facility.repository.FacilityRepository;

/**
 * 읽고 다듬은 편의시설 줄을 {@code facility} 에 넣는다 (S15P21E201-2052). 한 덩어리(같은 종류)가 한 트랜잭션이다.
 *
 * <p>🔴 <b>멱등</b> — 같은 파일을 두 번 돌려도 행이 안 는다. {@code (종류, 원천 번호)} 가 이미 있으면 값을 갱신하고, 없으면 넣는다.
 * 값이 하나도 안 바뀐 행은 갱신(UPDATE)도 나가지 않는다 — 두 번째 실행은 DB 에 아무 일도 안 한다({@link Facility#refresh}).
 *
 * <p>이 클래스는 <b>지우지 않는다.</b> 새 수집분에서 사라진 행도 그대로 둔다 — 원천이 잠깐 빠뜨린 것을 지우면 앱의 «근처 약국» 이
 * 줄어든다. 얼마나 남는지는 실행기가 로그에 센다.
 */
@Component
@Profile({ "db", "dev" })
public class FacilityLoader {

	private final FacilityRepository repository;

	public FacilityLoader(FacilityRepository repository) {
		this.repository = repository;
	}

	/**
	 * 한 덩어리를 넣는다. 덩어리의 행은 모두 같은 종류여야 한다.
	 *
	 * @throws IllegalArgumentException 종류가 섞여 있을 때
	 */
	@Transactional
	public Result saveChunk(List<FacilityRow> rows, String datasetVersion, OffsetDateTime collectedAt) {
		if (rows.isEmpty()) {
			return Result.empty();
		}
		FacilityKind kind = rows.get(0).kind();
		for (FacilityRow row : rows) {
			if (row.kind() != kind) {
				throw new IllegalArgumentException("한 덩어리에 종류가 섞였다: " + kind + " · " + row.kind());
			}
		}
		Map<String, Facility> existing = new HashMap<>();
		this.repository.findByKindAndSourceIdIn(kind, rows.stream().map(FacilityRow::sourceId).toList())
				.forEach(facility -> existing.put(facility.getSourceId(), facility));

		List<Facility> fresh = new ArrayList<>();
		int updated = 0;
		int unchanged = 0;
		for (FacilityRow row : rows) {
			Facility current = existing.get(row.sourceId());
			if (current == null) {
				fresh.add(Facility.create(kind, row.sourceId(), row.name(), row.address(), row.lat(), row.lng(),
						row.detail(), datasetVersion, collectedAt));
			}
			else if (current.refresh(row.name(), row.address(), row.lat(), row.lng(), row.detail(), datasetVersion,
					collectedAt)) {
				updated++;
			}
			else {
				unchanged++;
			}
		}
		this.repository.saveAll(fresh);
		return new Result(fresh.size(), updated, unchanged);
	}

	/**
	 * 한 번의 결과.
	 *
	 * @param created 새로 넣은 행
	 * @param updated 이미 있고 값이 바뀌어 고친 행
	 * @param unchanged 이미 있고 값이 같아 손대지 않은 행
	 */
	public record Result(int created, int updated, int unchanged) {

		public static Result empty() {
			return new Result(0, 0, 0);
		}

		public Result plus(Result other) {
			return new Result(this.created + other.created, this.updated + other.updated,
					this.unchanged + other.unchanged);
		}

		/** 파일의 행 가운데 이미 DB 에 있던 것. */
		public int alreadyThere() {
			return this.updated + this.unchanged;
		}

		@Override
		public String toString() {
			return "새 행 " + this.created + " · 바뀌어 고친 행 " + this.updated + " · 그대로 " + this.unchanged;
		}
	}
}
