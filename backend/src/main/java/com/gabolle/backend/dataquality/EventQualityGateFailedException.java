package com.gabolle.backend.dataquality;

/**
 * 품질 게이트가 통과하지 못했다 — S15P21E201-546.
 *
 * <p>🔴 이 예외가 던져지는 것이 이 티켓의 완료 기준이다 — <b>"오염 fixture 는 0이 아닌 종료
 * 코드로 실패한다"</b>. 경고 로그로 남기고 넘어가면 게이트가 아니라 알림이 되고, 알림은
 * 아무도 안 본다.
 */
public class EventQualityGateFailedException extends RuntimeException {

	private final EventQualityReport report;

	public EventQualityGateFailedException(EventQualityReport report) {
		super("이벤트 품질 게이트 실패 (" + report.datasetVersion() + "): " + report.failureSummary());
		this.report = report;
	}

	public EventQualityReport getReport() {
		return this.report;
	}
}
