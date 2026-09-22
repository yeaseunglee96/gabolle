package com.gabolle.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.gabolle.backend.itinerary.application.ActorNames;
import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryChangedByMember;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.notification.application.PushMessage;
import com.gabolle.backend.notification.application.PushSender;
import com.gabolle.backend.notification.application.PushTokenService;
import com.gabolle.backend.notification.application.TripPushNotifier;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripMemberJoined;
import com.gabolle.backend.trip.domain.TripMemberRoleChanged;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 여행 활동 → 동행자 폰 알림 — S15P21E201-1391 (2/2).
 *
 * <p>여기서 지키는 것은 <b>누구에게 가는가</b>와 <b>무슨 말이 뜨는가</b> 둘이다. 실제로 Expo 에
 * 어떻게 실려 나가는지는 {@code ExpoPushSenderTest} 가 본다.
 */
class TripPushNotifierTest {

	private static final String TRIP_ID = "trp_1";

	private static final String ITINERARY_ID = "itn_1";

	private static final String ME = "usr_me";

	private static final String MATE = "usr_mate";

	private static final String VIEWER = "usr_viewer";

	private ItineraryRepository itineraries;

	private TripRepository trips;

	private ActorNames actorNames;

	private PushTokenService pushTokens;

	private RecordingSender sender;

	private TripPushNotifier notifier;

	/** 보낸 것을 그대로 적어 두는 발송기 — 「누구에게 무슨 말이」를 그대로 볼 수 있다. */
	private static final class RecordingSender implements PushSender {

		private final List<Sent> sent = new ArrayList<>();

		private List<String> reportAsGone = List.of();

		record Sent(List<String> tokens, PushMessage message) {
		}

		@Override
		public List<String> send(List<String> tokens, PushMessage message) {
			this.sent.add(new Sent(List.copyOf(tokens), message));
			return this.reportAsGone;
		}
	}

	@BeforeEach
	void setUp() {
		this.itineraries = mock(ItineraryRepository.class);
		this.trips = mock(TripRepository.class);
		this.actorNames = mock(ActorNames.class);
		this.pushTokens = mock(PushTokenService.class);
		this.sender = new RecordingSender();
		this.notifier = new TripPushNotifier(this.itineraries, this.trips, this.actorNames, this.pushTokens,
				this.sender);

		when(this.itineraries.findById(ITINERARY_ID))
				.thenReturn(Optional.of(new Itinerary(ITINERARY_ID, TRIP_ID, 5)));
		when(this.trips.findById(TRIP_ID)).thenReturn(Optional.of(trip("부산 바다 2박 3일")));
		when(this.trips.findMembers(TRIP_ID)).thenReturn(List.of(
				TripMember.owner("tm_1", TRIP_ID, ME, Instant.parse("2026-09-01T00:00:00Z")),
				TripMember.invited("tm_2", TRIP_ID, MATE, TripMember.Role.EDITOR, Instant.parse("2026-09-02T00:00:00Z")),
				TripMember.invited("tm_3", TRIP_ID, VIEWER, TripMember.Role.VIEWER,
						Instant.parse("2026-09-03T00:00:00Z"))));
		when(this.actorNames.resolve(anyCollection())).thenReturn(Map.of(ME, "수민", MATE, "지훈"));
		// 사람마다 「이 사람 토큰」 하나씩 — 누가 받았는지를 토큰으로 되짚는다.
		when(this.pushTokens.tokensOf(anyCollection())).thenAnswer((call) -> {
			Collection<?> userIds = call.getArgument(0);
			return userIds.stream().map((id) -> "tok:" + id).toList();
		});
	}

	private static Trip trip(String title) {
		return Trip.builder().tripId(TRIP_ID).createdBy(ME).ownerType(Trip.OwnerType.USER)
				.startDate(LocalDate.of(2026, 10, 1)).finishDate(LocalDate.of(2026, 10, 3))
				.partySize(2).timezone("Asia/Seoul").title(title)
				.createdAt(Instant.parse("2026-09-01T00:00:00Z")).build();
	}

	private RecordingSender.Sent onlySent() {
		assertThat(this.sender.sent).hasSize(1);
		return this.sender.sent.get(0);
	}

	@Test
	@DisplayName("🔴 순서를 바꾼 본인에게는 안 간다 — 자기가 방금 누르고 화면을 보고 있다")
	void theEditorDoesNotGetTheirOwnChange() {
		this.notifier.onItineraryChanged(
				new ItineraryChangedByMember(ITINERARY_ID, 6, ItineraryVersion.Operation.REORDER, ME));

		assertThat(onlySent().tokens()).containsExactlyInAnyOrder("tok:" + MATE, "tok:" + VIEWER);
	}

	@Test
	@DisplayName("🔴 일정 생성 완료만 만든 본인에게도 간다 — 오래 걸려서 앱을 닫고 기다린 사람이다")
	void creationReachesTheRequesterToo() {
		this.notifier.onItineraryChanged(
				new ItineraryChangedByMember(ITINERARY_ID, 1, ItineraryVersion.Operation.CREATE, ME));

		assertThat(onlySent().tokens()).containsExactlyInAnyOrder("tok:" + ME, "tok:" + MATE, "tok:" + VIEWER);
	}

	@Test
	@DisplayName("일정 생성 문구에는 사람이 안 들어간다 — 본인에게도 가므로 자기 이름이 뜨면 이상하다")
	void creationCopyNamesNobody() {
		this.notifier.onItineraryChanged(
				new ItineraryChangedByMember(ITINERARY_ID, 1, ItineraryVersion.Operation.CREATE, ME));

		PushMessage message = onlySent().message();
		assertThat(message.title()).isEqualTo("일정이 만들어졌어요");
		assertThat(message.body()).isEqualTo("「부산 바다 2박 3일」 — 확인하고 저장해 주세요.");
		assertThat(message.body()).doesNotContain("수민");
	}

	@Test
	@DisplayName("동행의 편집은 앱 알림 목록과 같은 말이다 — 「수민님이 순서를 바꿨어요」")
	void editCopyMatchesTheInAppFeed() {
		this.notifier.onItineraryChanged(
				new ItineraryChangedByMember(ITINERARY_ID, 6, ItineraryVersion.Operation.REORDER, ME));

		PushMessage message = onlySent().message();
		assertThat(message.title()).isEqualTo("일정 순서가 바뀌었어요");
		assertThat(message.body()).isEqualTo("「부산 바다 2박 3일」 — 수민님이 순서를 바꿨어요.");
	}

	@Test
	@DisplayName("🔴 누르면 갈 자리는 data.href 다 — 앱이 읽는 칸 이름이 그것이고, 값은 일정 번호로 만든다")
	void hrefPointsAtTheItineraryScreen() {
		this.notifier.onItineraryChanged(
				new ItineraryChangedByMember(ITINERARY_ID, 6, ItineraryVersion.Operation.REORDER, ME));

		PushMessage message = onlySent().message();
		// 앱의 /trips/[id]/itinerary 는 여행 번호가 아니라 «일정 번호» 를 받는다
		// (frontend/app/(tabs)/trips.tsx 의 openItinerary).
		assertThat(message.href()).isEqualTo("/trips/" + ITINERARY_ID + "/itinerary");
		assertThat(message.itineraryId()).isEqualTo(ITINERARY_ID);
	}

	@Test
	@DisplayName("이름이 없는 사람(탈퇴)의 편집은 여행 이름만 남는다 — 「누군가」를 지어내지 않는다")
	void aDeletedActorLeavesJustTheTripName() {
		when(this.actorNames.resolve(anyCollection())).thenReturn(Map.of());

		this.notifier.onItineraryChanged(
				new ItineraryChangedByMember(ITINERARY_ID, 6, ItineraryVersion.Operation.REORDER, ME));

		assertThat(onlySent().message().body()).isEqualTo("「부산 바다 2박 3일」");
	}

	@Test
	@DisplayName("이름을 안 붙인 여행은 기간이 이름이 된다 — 서버가 「내 여행」 같은 말을 붙이지 않는다")
	void anUnnamedTripFallsBackToItsDates() {
		when(this.trips.findById(TRIP_ID)).thenReturn(Optional.of(trip(null)));

		this.notifier.onItineraryChanged(
				new ItineraryChangedByMember(ITINERARY_ID, 6, ItineraryVersion.Operation.REORDER, ME));

		assertThat(onlySent().message().body()).isEqualTo("「2026-10-01 ~ 2026-10-03」 — 수민님이 순서를 바꿨어요.");
	}

	@ParameterizedTest
	@EnumSource(ItineraryVersion.Operation.class)
	@DisplayName("🔴 판 종류가 무엇이든 빈 알림이 뜨지 않는다 — 종류가 늘면 여기가 먼저 잡는다")
	void everyOperationHasSomethingToSay(ItineraryVersion.Operation operation) {
		this.notifier.onItineraryChanged(new ItineraryChangedByMember(ITINERARY_ID, 6, operation, ME));

		PushMessage message = onlySent().message();
		assertThat(message.title()).isNotBlank();
		assertThat(message.body()).isNotBlank();
	}

	@Test
	@DisplayName("동행이 들어오면 원래 있던 사람들에게 간다 — 들어온 본인은 뺀다")
	void joiningTellsTheOthers() {
		this.notifier.onMemberJoined(new TripMemberJoined(TRIP_ID, MATE, TripMember.Role.EDITOR));

		RecordingSender.Sent sent = onlySent();
		assertThat(sent.tokens()).containsExactlyInAnyOrder("tok:" + ME, "tok:" + VIEWER);
		assertThat(sent.message().title()).isEqualTo("동행이 합류했어요");
		assertThat(sent.message().body()).isEqualTo("「부산 바다 2박 3일」 — 지훈님이 함께하기로 했어요.");
		assertThat(sent.message().href()).isEqualTo("/" + TRIP_ID + "/collaborate");
	}

	@Test
	@DisplayName("🔴 자격 변경은 당사자 한 사람에게만 간다 — 남에게는 「누가 누구를 강등했다」일 뿐이다")
	void roleChangeReachesOnlyTheOneWhoseRoleChanged() {
		this.notifier.onRoleChanged(new TripMemberRoleChanged(TRIP_ID, MATE, TripMember.Role.VIEWER, ME));

		RecordingSender.Sent sent = onlySent();
		assertThat(sent.tokens()).containsExactly("tok:" + MATE);
		assertThat(sent.message().title()).isEqualTo("이제 일정을 보기만 할 수 있어요");
		assertThat(sent.message().body()).isEqualTo("「부산 바다 2박 3일」 — 수민님이 역할을 바꿨어요.");
	}

	@Test
	@DisplayName("편집 권한을 주면 그렇게 말한다")
	void promotionSaysWhatChanged() {
		this.notifier.onRoleChanged(new TripMemberRoleChanged(TRIP_ID, MATE, TripMember.Role.EDITOR, ME));

		assertThat(onlySent().message().title()).isEqualTo("이제 일정을 함께 고칠 수 있어요");
	}

	@Test
	@DisplayName("🔴 Expo 가 「없는 기기」라고 한 토큰은 표에서 지운다 — 안 지우면 죽은 기기가 쌓인다")
	void tokensExpoCallsDeadAreForgotten() {
		this.sender.reportAsGone = List.of("tok:" + VIEWER);

		this.notifier.onItineraryChanged(
				new ItineraryChangedByMember(ITINERARY_ID, 6, ItineraryVersion.Operation.REORDER, ME));

		verify(this.pushTokens).forget(List.of("tok:" + VIEWER));
	}

	@Test
	@DisplayName("알림을 켠 사람이 하나도 없으면 보내지 않는다 — 고장이 아니라 흔한 일이다")
	void nobodyWithATokenMeansNoCall() {
		when(this.pushTokens.tokensOf(anyCollection())).thenReturn(List.of());

		this.notifier.onItineraryChanged(
				new ItineraryChangedByMember(ITINERARY_ID, 6, ItineraryVersion.Operation.REORDER, ME));

		assertThat(this.sender.sent).isEmpty();
		verify(this.pushTokens, never()).forget(anyList());
	}

	@Test
	@DisplayName("혼자 하는 여행은 편집해도 아무에게도 안 간다 — 자기 자신뿐이다")
	void aSoloTripSendsNothingOnEdit() {
		when(this.trips.findMembers(TRIP_ID)).thenReturn(
				List.of(TripMember.owner("tm_1", TRIP_ID, ME, Instant.parse("2026-09-01T00:00:00Z"))));

		this.notifier.onItineraryChanged(
				new ItineraryChangedByMember(ITINERARY_ID, 6, ItineraryVersion.Operation.REORDER, ME));

		assertThat(this.sender.sent).isEmpty();
	}

	@Test
	@DisplayName("🔴 보내다 터져도 밖으로 안 던진다 — 커밋이 이미 끝나 되돌릴 것이 없다")
	void afailureNeverEscapes() {
		PushSender exploding = mock(PushSender.class);
		when(exploding.send(anyList(), any(PushMessage.class))).thenThrow(new IllegalStateException("Expo 가 죽었다"));
		TripPushNotifier fragile = new TripPushNotifier(this.itineraries, this.trips, this.actorNames,
				this.pushTokens, exploding);

		// 던지면 이 줄에서 시험이 빨개진다. 편집은 이미 성공으로 끝났는데 여기서 터지면
		// 스택트레이스만 아무 맥락 없이 찍힌다.
		fragile.onItineraryChanged(
				new ItineraryChangedByMember(ITINERARY_ID, 6, ItineraryVersion.Operation.REORDER, ME));
	}

	@Test
	@DisplayName("없는 일정으로 온 사건은 조용히 지나간다")
	void anUnknownItineraryIsIgnored() {
		when(this.itineraries.findById("itn_gone")).thenReturn(Optional.empty());

		this.notifier.onItineraryChanged(
				new ItineraryChangedByMember("itn_gone", 6, ItineraryVersion.Operation.REORDER, ME));

		assertThat(this.sender.sent).isEmpty();
	}
}
