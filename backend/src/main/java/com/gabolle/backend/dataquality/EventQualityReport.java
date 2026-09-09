package com.gabolle.backend.dataquality;

import java.time.LocalDate;
import java.util.List;

/**
 * 품질 게이트 한 번의 판정 결과 — S15P21E201-546.
 *
 * <p>비율과 위반 건수를 나눠 담는다. <b>비율은 추세를 보는 값이고 위반은 통과를 막는 값이다.</b>
 * 섞으면 "결측률이 3% 니까 괜찮다" 같은 판단이 들어오고, 그건 게이트가 아니라 의견이 된다.
 *
 * @param violations 사람이 읽을 위반 요약. 비어 있으면 통과다
 */
public record EventQualityReport(
		LocalDate reportDate,
		String datasetVersion,

		long eventsTotal,
		long impressionsTotal,
		long candidatesTotal,

		double schemaValidRate,
		double duplicateRate,
		double requestIdMissingRate,
		double candidateFeatureMissingRate,

		long orphanImpressions,
		long rankMismatches,
		long versionMissing,
		long piiViolations,
		long failVerdictExposed,

		List<String> violations) {

	public EventQualityReport {
		violations = (violations == null) ? List.of() : List.copyOf(violations);
	}

	/**
	 * 🔴 통과 여부를 저장된 값에서 다시 계산하지 않는다. 위반 목록이 비었는가 하나로만 본다.
	 *
	 * <p>두 곳에서 계산하면 어느 날 한쪽만 고쳐지고, 그때 리포트는 "통과" 라고 적혀 있는데
	 * 위반 건수가 0 이 아니다. DB 에도 같은 뜻의 CHECK 를 걸어 두었다.
	 */
	public boolean passed() {
		return this.violations.isEmpty();
	}

	/** 실패 이유를 한 줄로 합친다. 통과면 {@code null} — DB 가 "실패인데 이유 없음" 을 거부한다. */
	public String failureSummary() {
		return passed() ? null : String.join(" / ", this.violations);
	}
}
