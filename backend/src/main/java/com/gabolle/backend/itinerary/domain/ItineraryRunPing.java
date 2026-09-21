package com.gabolle.backend.itinerary.domain;

import java.time.Instant;

/**
 * 여행 진행 중에 받은 위치 한 점.
 *
 * <p>🔴 <b>버리는 로그가 아니다.</b> 나중에 「구간별 실제 이동시간」과 「장소별 체류시간」을
 * 이 궤적에서 낸다. 지금 저장소에는 장소별 체류시간이 아예 없어서, 일정은 하루를 곳 수로
 * 나눈 값을 쓰고 있다({@code ItineraryDraftService}). 그 값을 실제로 재려면 사람이 실제로
 * 어디에 얼마나 있었는지가 필요하고, 그것을 남기는 자리가 여기다.
 *
 * <p>🔴 <b>동시에 이 저장소에서 제일 민감한 자료다</b> — 사람이 언제 어디 있었는지의 기록이다.
 * 탈퇴하면 연쇄 삭제에 기대지 말고 명시적으로 지운다({@code AccountDeletionService}).
 * 보존은 <b>여행 종료 후 1년</b>이고 별도 정리 잡이 지운다 — 여행에 계절성이 있어 한 해치가
 * 있어야 분석이 된다는 판단이다(2026-09-21 결정).
 *
 * @param recordedAt 기기가 그 점을 찍은 시각. 판정과 궤적의 차례는 이 시각이 정한다
 * @param receivedAt 서버가 받은 시각. 배치로 모아 올리면 {@code recordedAt} 과 몇 분씩
 *     벌어진다 — 하나만 두면 「기기 시계가 틀렸나」와 「망이 끊겼었나」를 영영 못 가른다
 */
public record ItineraryRunPing(String pingId, String itineraryId, double lat, double lng,
		Instant recordedAt, Instant receivedAt) {

	public ItineraryRunPing {
		if (lat < -90 || lat > 90) {
			throw new IllegalArgumentException("위도 범위를 벗어났다: " + lat);
		}
		if (lng < -180 || lng > 180) {
			throw new IllegalArgumentException("경도 범위를 벗어났다: " + lng);
		}
		if (recordedAt == null) {
			// 시각이 없으면 궤적의 차례를 못 정하고, 지금 시각으로 채우면 배치로 올린 옛 점이
			// 전부 「방금」이 된다.
			throw new IllegalArgumentException("위치를 찍은 시각이 필요하다");
		}
	}
}
