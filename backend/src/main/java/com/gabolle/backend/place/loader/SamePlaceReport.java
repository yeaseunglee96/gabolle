package com.gabolle.backend.place.loader;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;

/**
 * 한 번의 적재에서 같은 곳이라 안 넣은 수와, 사람이 봐야 할 짝 (S15P21E201-1620). 적재기 여러 덩어리가 하나를 같이 쓴다.
 *
 * <p>사람 확인 목록은 적재 로그 말고 <b>파일로도</b> 떨어뜨린다({@link #writeCsv}) — 나중에 사람이 짝마다 「같음/다름」을
 * 적는 목록이다. 「다름」으로 판정된 것만 다음 적재나 마이그레이션으로 넣는다. 장소 합치기(S15P21E201-1619)의 사람 확인
 * 목록과 같은 모양이다.
 */
public final class SamePlaceReport {

	/** 로그에 예로 찍는 막힌 줄 수 — 전부 찍으면 로그가 목록이 된다. 수는 전부 센다. */
	private static final int EXAMPLES = 10;

	private int blocked;

	private final List<String> blockedExamples = new ArrayList<>();

	private final List<Row> reviews = new ArrayList<>();

	/** 사람 확인 한 줄. */
	public record Row(SamePlaceRule.Kind kind, SamePlaceGuard.Candidate candidate, SamePlaceGuard.Verdict verdict) {
	}

	/** 판정 하나를 센다. */
	void record(SamePlaceGuard.Candidate candidate, SamePlaceGuard.Verdict verdict) {
		if (verdict.kind() == SamePlaceRule.Kind.SAME) {
			this.blocked++;
			if (this.blockedExamples.size() < EXAMPLES) {
				this.blockedExamples.add("%s(%s %s) = %s(%s) %dm".formatted(candidate.name(), candidate.sourceType(),
						candidate.sourceId(), verdict.existingName(), verdict.existingSource(),
						Math.round(verdict.distanceM())));
			}
			return;
		}
		this.reviews.add(new Row(verdict.kind(), candidate, verdict));
	}

	/** 같은 곳이 있어 안 넣은 수. */
	public int blocked() {
		return this.blocked;
	}

	/** 사람이 봐야 해서 안 넣은 짝. */
	public List<Row> reviews() {
		return List.copyOf(this.reviews);
	}

	/** 적재 로그 한 줄. */
	public String summary() {
		long chain = this.reviews.stream().filter((r) -> r.kind() == SamePlaceRule.Kind.REVIEW_CHAIN).count();
		return "같은 장소가 있어 안 넣음 %d곳 · 사람 확인 %d곳(체인 같은 이름 30~150m %d · 해변·산책로·공원 같은 이름 150m~1km %d)"
				.formatted(this.blocked, this.reviews.size(), chain, this.reviews.size() - chain)
				+ (this.blockedExamples.isEmpty() ? "" : " — 예: " + String.join(" · ", this.blockedExamples));
	}

	/**
	 * 사람 확인 목록을 CSV 로 쓴다. 짝이 없어도 머리줄만 있는 파일을 쓴다 — 파일이 없으면 「없었다」와 「안 돌았다」를 못
	 * 가른다. 엑셀이 한글을 깨뜨리지 않게 BOM 을 붙인다.
	 */
	public void writeCsv(Path path) throws IOException {
		if (path.getParent() != null) {
			Files.createDirectories(path.getParent());
		}
		try (Writer out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			out.write('﻿');
			out.write(String.join(",", "번호", "왜 사람이 보나", "새 이름", "있는 이름", "거리 m", "새 출처", "새 원천 번호",
					"있는 장소 번호", "있는 출처", "새 갈래", "있는 갈래", "새 위도", "새 경도", "판정(같음/다름)"));
			out.write("\r\n");
			int n = 0;
			for (Row row : this.reviews) {
				SamePlaceGuard.Candidate c = row.candidate();
				SamePlaceGuard.Verdict v = row.verdict();
				out.write(String.join(",", String.valueOf(++n), cell(why(row.kind())), cell(c.name()),
						cell(v.existingName()), String.valueOf(Math.round(v.distanceM())), cell(c.sourceType()),
						cell(c.sourceId()), cell(String.valueOf(v.existingPlaceId())), cell(v.existingSource()),
						cell(c.category()), cell(v.existingCategory()), String.format(Locale.ROOT, "%.6f", c.lat()),
						String.format(Locale.ROOT, "%.6f", c.lng()), ""));
				out.write("\r\n");
			}
		}
	}

	/**
	 * 적재 끝에 실행기가 부른다 — 요약과 사람 확인 줄을 로그에 찍고, 입력 파일 옆에 CSV 를 쓴다
	 * ({@code same-place-review-<출처>-<수집분>.csv}).
	 *
	 * <p>CSV 를 못 써도 적재를 실패로 돌리지 않는다 — 장소는 이미 들어갔고, 사람 확인 줄은 로그에 이미 다 있다.
	 */
	public void finish(Path input, String sourceType, String datasetVersion, Logger logger) {
		logger.info("같은 곳 검사(S15P21E201-1620) — {}", summary());
		for (Row row : this.reviews) {
			logger.info("  사람 확인 — {} · 새 {}({} {}) · 있는 {}({} {}) · {}m", why(row.kind()), row.candidate().name(),
					row.candidate().sourceType(), row.candidate().sourceId(), row.verdict().existingName(),
					row.verdict().existingSource(), row.verdict().existingPlaceId(), Math.round(row.verdict().distanceM()));
		}
		Path csv = input.toAbsolutePath().resolveSibling("same-place-review-%s-%s.csv".formatted(sourceType,
				datasetVersion));
		try {
			writeCsv(csv);
			logger.info("사람 확인 목록 {}줄을 썼다 — {} (판정 칸에 같음/다름을 적는다)", this.reviews.size(), csv);
		}
		catch (IOException e) {
			logger.warn("사람 확인 목록 파일을 못 썼다 — {} ({}). 목록은 위 로그에 있다", csv, e.getMessage());
		}
	}

	/** 같은 곳이라 안 넣은 수와 사람 확인 수를 더한 것 — 실행기의 「이미 있어 건너뛴」 수에서 뺀다. */
	public int notInserted() {
		return this.blocked + this.reviews.size();
	}

	private static String why(SamePlaceRule.Kind kind) {
		return (kind == SamePlaceRule.Kind.REVIEW_CHAIN) ? "체인 같은 이름 30~150m" : "해변·산책로·공원 같은 이름 150m~1km";
	}

	/** 쉼표·따옴표·줄바꿈이 든 칸만 따옴표로 감싼다. */
	private static String cell(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}
}
