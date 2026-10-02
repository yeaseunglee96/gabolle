package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.gabolle.backend.recommendation.domain.FallbackMode;

/**
 * 완성된 일정표 조회 응답. 프론트가 이미 코드에 박아 놓고 부르는 모양을 그대로 따른다.
 */
public record ItineraryDetailResponse(
		String id,
		/** {@code itineraries} 표에 제목 칸이 없어 여행 기간으로 지어낸 값이다. */
		String title,
		int version,
		List<Day> days,
		/** 항목 비용의 합. 전부 {@code null} 이면 {@code null} — {@code 0} 은 "무료"라는 다른 사실이다. */
		Integer totalEstimatedCostKrw,
		/** 구간 도보 거리의 합. 하나도 없으면 {@code 0} 이 아니라 {@code null} — "안 걸었다"와
		 * "안 쟀다"는 다르다. */
		Integer totalWalkingMeters,
		/** 이 판을 만든 추천 요청의 fallback_mode. 사용자가 손으로 만든 판이면 {@code null}. */
		FallbackMode fallbackMode,

		/** {@code OWNER} · {@code EDITOR} · {@code VIEWER} — {@code TripMember.Role} 의 이름 그대로. */
		String myRole,
		/** {@code myRole} 이 {@code OWNER} 나 {@code EDITOR} 면 {@code true} — VIEWER 는 {@code false}. */
		boolean canEdit,
		/**
		 * {@code itinerary_versions.warning_codes} 를 그대로 옮긴다. 경고가 없으면 빈 배열이지
		 * {@code null} 이 아니다.
		 */
		List<String> warningCodes,

		/** {@code trip.trip_id} 그대로. 일정은 반드시 여행 하나에 속하므로 비지 않는다. */
		String tripId,

		/**
		 * 이 일정에서 휠체어 접근을 안 재 본 항목 수. 0 보다 크면 화면이 「확인 안 된 곳이
		 * 있다」고 알린다. 참거짓 칸을 따로 두지 않는다 — 이 값이 0 보다 큰지가 곧 그 뜻이다.
		 */
		int accessibilityUnverifiedCount,

		/** 이 여행을 몇 명이 가는가. {@code trip.party_size} 그대로다. */
		int partySize,

		/**
		 * 예산 상한(원) — 일정을 짤 때 넘지 않게 한 값과 같다: {@code 반올림(예산 × (1 + 허용 폭 20%))}
		 * ({@code BudgetAllowance}). 합계가 예산을 넘었어도 이 값 안이면 앱이 경고 대신 부드럽게 알린다
		 * (S15P21E201-1743, 사용자 결정 2026-09-26). 예산을 안 정한 여행이면 {@code null}.
		 */
		Integer budgetCapKrw,

		/**
		 * 이 여행의 걷는 길을 계단·급경사를 피해 찾았나 — 이동 조건에 휠체어·유아차·계단 피하기 가운데 하나를
		 * 「골랐다」면 {@code true}. 일정 구간을 잴 때({@code ItineraryLegPlanner.needsStepFree})와 같은 규칙 한 곳에서
		 * 센다. 앱이 한 구간의 길을 다시 물을 때 이 값을 {@code /routes?stepFree=} 에 그대로 실으면 일정과 같은 길이
		 * 나온다.
		 */
		boolean stepFree,

		/**
		 * 사용자가 추천 단계에서 「경사를 피하고 싶어요」를 <b>골랐나</b>(S15P21E201-1895). 지도가 이 값이 {@code true} 일
		 * 때만 경로 선을 경사대로 칠한다 — 안 골랐는데 칠하면 조건과 무관한 색이 된다. 안 골랐거나 「상관없어요」거나 답을
		 * 못 읽으면 {@code false}. 여행의 가장 최신 선호 판({@code PreferenceSnapshot})에서 읽는다.
		 */
		boolean slopeAvoid,

		/**
		 * 사용자가 「그늘길 우선」을 <b>골랐나</b>(S15P21E201-1895). {@code slopeAvoid} 와 같은 규칙이다. 둘 다
		 * {@code true} 면 지도가 경사와 그늘을 합친 점수로 칠한다.
		 */
		boolean shadePrefer) {

	/** 경사·그늘 선택 칸 이전의 생성자 — 조건을 안 골랐다고 본다. */
	public ItineraryDetailResponse(String id, String title, int version, List<Day> days,
			Integer totalEstimatedCostKrw, Integer totalWalkingMeters, FallbackMode fallbackMode, String myRole,
			boolean canEdit, List<String> warningCodes, String tripId, int accessibilityUnverifiedCount,
			int partySize, Integer budgetCapKrw, boolean stepFree) {
		this(id, title, version, days, totalEstimatedCostKrw, totalWalkingMeters, fallbackMode, myRole, canEdit,
				warningCodes, tripId, accessibilityUnverifiedCount, partySize, budgetCapKrw, stepFree, false, false);
	}

	/** 계단 피하기 칸 이전의 생성자 — 보통 길로 잰 일정이다. */
	public ItineraryDetailResponse(String id, String title, int version, List<Day> days,
			Integer totalEstimatedCostKrw, Integer totalWalkingMeters, FallbackMode fallbackMode, String myRole,
			boolean canEdit, List<String> warningCodes, String tripId, int accessibilityUnverifiedCount,
			int partySize, Integer budgetCapKrw) {
		this(id, title, version, days, totalEstimatedCostKrw, totalWalkingMeters, fallbackMode, myRole, canEdit,
				warningCodes, tripId, accessibilityUnverifiedCount, partySize, budgetCapKrw, false, false, false);
	}

	/**
	 * 길 조각 하나 — 경로 API({@code /routes})의 {@code pieces} 와 같은 모양이다.
	 * {@code travelPath[from]..travelPath[to]}(둘 다 포함)의 경사(%, 방향 없음 · 모르면 {@code null} — 0(평지)과
	 * 다르다)와 계단 여부, 그늘.
	 *
	 * @param shade 그늘(0~1, 1 이 하루 종일 그늘, 0.1 단위 — 건물 그림자 하루 평균). 모르면 {@code null} — 0(볕)과
	 *        다르다. 그늘 값을 재 둔 여섯 지역 밖·다리·터널·이 칸이 생기기 전(2026-09-30)에 만든 판이 그렇다
	 */
	public record Piece(int from, int to, Double slopePercent, boolean stairs, Double shade) {

		/** 그늘 칸이 생기기 전의 모양 — 그늘을 모르는 조각이다. */
		public Piece(int from, int to, Double slopePercent, boolean stairs) {
			this(from, to, slopePercent, stairs, null);
		}
	}

	/**
	 * 여행 기간의 날짜 하나 — 항목이 0개인 날도 포함된다(빈 {@code items}).
	 *
	 * @param returnLeg 그날 마지막 방문지에서 돌아가는 이동(S15P21E201-1565). 돌아갈 자리를 모르면 {@code null}
	 * @param start     그날 어디서 출발하나(S15P21E201-1581). 첫 방문지로 들어오는 구간이 여기서 잰 것이다.
	 *                  출발지도 모르는 옛 여행이면 {@code null}
	 */
	public record Day(String date, List<Item> items, ReturnLeg returnLeg, Start start) {

		/** 돌아가는 이동도 출발 자리도 모르는 날. */
		public Day(String date, List<Item> items) {
			this(date, items, null, null);
		}
	}

	/**
	 * 하루를 여는 자리 — 첫날이거나 숙소를 모르면 여행 출발지, 둘째 날부터는 숙소.
	 *
	 * @param kind  {@code ORIGIN}(여행 출발지) · {@code LODGING}(숙소)
	 * @param label 숙소 이름 또는 동네 이름. 출발지면 {@code null} — 출발지 이름은 저장하지 않는다
	 */
	public record Start(String kind, String label, double lat, double lng) {
	}

	/**
	 * 하루 끝에 돌아가는 이동 — 마지막 날이 아니면 숙소로, 마지막 날이면 여행 출발지로.
	 *
	 * @param kind            {@code LODGING}(숙소) · {@code ORIGIN}(여행 출발지)
	 * @param label           숙소 이름 또는 동네 이름. 출발지면 {@code null} — 화면이 「출발지」로 적는다
	 * @param durationMin     못 쟀으면 {@code null}
	 * @param travelDataStatus {@code VERIFIED}·{@code ESTIMATED}. 어림값을 잰 값처럼 그리지 않게 싣는다
	 */
	public record ReturnLeg(String kind, String label, double lat, double lng, Integer durationMin,
			Integer distanceM, String travelDataStatus) {
	}

	public record Item(
			/** {@code itinerary_item.item_key} — PK 가 아니다. 판이 바뀌어도 같은 항목을 가리킨다. */
			String id,
			/** ISO-8601. {@code start_time} 이 없으면 {@code null}. */
			String startsAt,
			/**
			 * 그곳을 떠나는 계획 시각. {@code startsAt} 과 같은 모양이고, {@code startsAt} 이 {@code null} 이면 이것도
			 * {@code null} 이다(둘 중 하나만 있는 일은 없다).
			 *
			 * <p>빈 시각은 이것으로 안다 — 같은 날 이웃한 A → B 에서 {@code B.startsAt − A.endsAt − (B.travelDurationMin ?? 0)}
			 * (S15P21E201-1667). 곳마다 갈래별로 머물고 남는 시간을 빈 시각으로 두는데, 새 항목 종류를 만들지 않고 두 시각의
			 * 차로만 드러낸다 — 이미 나간 앱이 모르는 항목을 받으면 깨질 수 있다.
			 */
			String endsAt,
			String title,
			/** 항상 {@code null} — {@code place} 표에 설명 칸이 없다. 주소를 대신 넣지 않는다. */
			String description,
			/**
			 * 이 여행 인원 <b>전체</b>가 그곳에서 쓸 값(원) — 대표 메뉴 한 그릇 값 × {@code partySize}. 1인분이 아니다
			 * (S15P21E201-1579). 모르면 {@code null} — {@code 0} 은 "무료"라는 다른 사실이다.
			 */
			Integer estimatedCostKrw,
			/** 이 항목으로 들어오는 구간의 도보 거리. 그 구간이 없거나 도보가 아니면 {@code null}. */
			Integer walkingMeters,
			boolean locked,
			/** {@code VERIFIED} · {@code ESTIMATED} · {@code UNKNOWN}. */
			String dataStatus,

			/**
			 * 실제로 도착한 시각. ISO-8601 + Asia/Seoul. 계획인 {@code startsAt} 을 덮어쓰지 않고
			 * 자리를 따로 둔다. 기록이 없으면 {@code null} 이고, 그때도 칸 자체는 응답에 있다.
			 */
			String actualArrivedAt,
			/** 실제로 출발한 시각. 없으면 {@code null} — 도착만 적고 출발은 안 적을 수 있다. */
			String actualDepartedAt,
			/**
			 * {@code place.place_id} 그대로. 항목은 반드시 장소 하나를 가리키므로 비지 않는다.
			 * 제목으로 장소를 다시 찾지 않는다 — 같은 이름의 가게가 여럿이다.
			 */
			String placeId,

			/**
			 * 이 방문지로 오는 데 걸리는 시간(분). 그 구간이 없으면(그날 첫 방문지 앞) {@code null} 이다.
			 */
			Integer travelDurationMin,
			/**
			 * {@code travelDurationMin} · {@code walkingMeters} 를 얼마나 믿을 수 있는가 —
			 * {@code VERIFIED} 길찾기 실제 응답 · {@code ESTIMATED} 직선거리 어림값 ·
			 * {@code UNKNOWN} 좌표가 없어 못 쟀다. 이 기능 이전에 만들어진 판은 {@code null}.
			 */
			String travelDataStatus,
			/**
			 * 이 항목으로 들어오는 구간의 이동 요금(원). {@code null} 은 모른다는 뜻이고
			 * {@code 0} 은 공짜라는 뜻이다. 값이 있는 것은 자동차 계열(택시·자가용·렌터카)뿐이다.
			 * 그 장소에 들어가는 돈인 {@code estimatedCostKrw} 와 더하지 않는다.
			 */
			Integer travelFareKrw,
			/**
			 * 이 항목으로 오는 구간이 <b>실제로 지나는 길</b>의 좌표 목록.
			 * {@code [[경도, 위도], …]} 순서다 — GeoJSON·지도 라이브러리와 같은 순서라 그대로
			 * 넘겨 그릴 수 있다.
			 *
			 * <p>🔴 <b>{@code null} 이면 직선을 그리라는 뜻이 아니라 「어느 길인지 모른다」는
			 * 뜻이다.</b> 길찾기 응답을 실제로 받은 구간에만 값이 있고, 직선거리로 어림잡은
			 * 구간은 비어 있다. 화면은 이 둘을 다르게 그린다 — 실제 길은 실선, 추정은 점선
			 * (S15P21E201-1234).
			 *
			 * <p>이 칸이 생기기 전(2026-09-22)에 만들어진 판은 전부 {@code null} 이다.
			 * 그때 어느 길로 갔는지는 남아 있지 않다.
			 */
			List<double[]> travelPath,

			/**
			 * {@code travelPath} 를 경사·계단이 같은 조각으로 나눈 것 — {@code [{from, to, slopePercent, stairs}, …]}.
			 * 번호는 {@code travelPath} 의 자리다. 🔴 {@code travelPath} 가 있을 때만 있고, 그중에서도 우리 보행 그래프가
			 * 찾은 걷기만 조각이 있다(자동차·대중교통·어림 구간은 {@code null}). 이 칸이 생기기 전(2026-09-29)에 만든 판도
			 * {@code null} 이다. 빈 배열은 나가지 않는다.
			 */
			List<Piece> travelPieces,

			/**
			 * 이 항목에 붙은 경고. {@code itinerary_item.warning_codes} 를 그대로 옮긴다. 경고가
			 * 없으면 빈 배열이지 {@code null} 이 아니다 — 판 수준 {@code warningCodes} 와 같다.
			 * 프런트는 {@code src/plan/warningLabels.ts} 에 짝이 없는 코드를 그리지 않으므로,
			 * 새 코드를 내보낼 때는 그 사전도 함께 본다.
			 */
			List<String> warningCodes,

			/**
			 * 이 방문지를 왜 넣었는가. {@code itinerary_item.reason_codes} 를 그대로 옮긴다(S15P21E201-1643). 없으면 빈
			 * 배열이지 {@code null} 이 아니다 — {@code warningCodes} 와 같다. 추천이 넣은 곳은 추천 결과의 이유 코드와 같은
			 * 어휘({@code NEAR_ORIGIN} · {@code TAG_MATCH_INTEREST} · {@code TOP_CONTRIBUTOR_<축>} 등)이고, 사용자가 손으로
			 * 넣은 곳은 {@code USER_ADDED} 다.
			 *
			 * <p>🔴 <b>그 일정을 만들 때의 코드다.</b> S15P21E201-1638 전에 만든 일정은 옛 규칙이라 모든 곳에
			 * {@code NEAR_ORIGIN} 이 있고 「가장 크게 기여」가 거의 늘 거리다. 다시 짜면 새 규칙으로 바뀐다.
			 */
			List<String> reasonCodes,

			/**
			 * 이 방문지를 낸 추천 요청 번호({@code itinerary_item.source_request_id}). 사용자가 손으로 더한 곳은 {@code null}
			 * (S15P21E201-1689). 앱이 추천 노출 이벤트({@code recommendation_impression})를 보낼 때 이 번호와 {@code placeId}
			 * 만 실으면 된다 — 순위·이유 코드·판은 서버가 이 번호로 채운다. 전에는 응답에 없어 노출을 보낼 수 없었다.
			 */
			String requestId,

			/**
			 * 이 방문지의 좌표. 모르면 {@code null} 이지 {@code 0} 이 아니다 — 위도 0·경도 0 은
			 * 기니만 바다 한가운데이고 지도에 실제로 점이 찍힌다.
			 */
			Double lat,
			Double lng,

			/**
			 * 장소의 영어 이름 — 장소 상세 응답의 {@code nameEn} 과 같다. 없으면 {@code null}. {@code title} 은 늘 한국어다
			 * (S15P21E201-1937). 화면이 다른 언어에서 이 이름을 고른다.
			 */
			String nameEn,
			/** 일본어·중국어 이름 — 장소 상세의 {@code localNames} 와 같은 키(ja·zh-Hans·zh-Hant). 비면 칸째 빠진다. */
			@JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> localNames) {
	}
}
