package com.gabolle.backend.notification.application;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.gabolle.backend.itinerary.application.ActorNames;
import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryChangedByMember;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripMemberJoined;
import com.gabolle.backend.trip.domain.TripMemberRoleChanged;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 여행에 무슨 일이 생기면 동행자 폰에 알림을 띄운다 — S15P21E201-1391 (2/2).
 *
 * <p>🔴 <b>{@code AFTER_COMMIT} 이어야 한다.</b> 편집 트랜잭션 안에서 보내면 되돌려진 편집으로도
 * 알림이 나간다 — 사람은 「수민님이 순서를 바꿨어요」를 보고 앱을 열었다가 아무것도 안 바뀐 것을
 * 본다. 그리고 알림은 <b>되부를 수 없다.</b> 잘못 보낸 것은 사과할 수도 없이 남의 잠금화면에
 * 이미 떠 있다.
 *
 * <p>🔴 <b>한 번도 던지지 않는다.</b> 커밋이 이미 끝나 되돌릴 것이 없다. 여기서 터지면
 * 스택트레이스만 아무 맥락 없이 찍히고, 정작 사용자의 편집은 성공으로 끝나 있다.
 * 메일 쪽 {@code StoryRemovalNotifier} 가 같은 이유로 같은 모양이다.
 *
 * <p><b>말은 앱과 같아야 한다.</b> 문구는 앱의 알림 목록
 * ({@code frontend/src/notifications/activityFeed.ts} 의 {@code noticeCopy})을 그대로 옮긴 것이다.
 * 목록에는 「장소가 빠졌어요」인데 폰에는 「항목 삭제」라고 뜨면 같은 일이 두 가지로 보인다.
 *
 * <p>🟡 <b>한 자리만 앱과 다르다 — {@code REPLAN_DAY}.</b> 앱의 {@code noticeCopy} 에 그 갈래가
 * 없어서 「일정이 바뀌었어요」로 뭉개진다. 없는 것을 따라 하지 않고 여기서는 뜻대로
 * 「남은 일정을 다시 계획했어요」라고 쓴다. 앱에 그 줄을 더하는 것은 따로 적어 둔다.
 *
 * <p>🟡 <b>알려진 한계 — 말이 한국어 하나다.</b> 앱은 사람마다 언어를 고르지만
 * ({@code tx(ko, en)}), 보내는 쪽에는 그 사람이 무슨 언어로 쓰는지가 없다. 기기 표
 * ({@code push_token})에 언어 칸을 두면 풀리는데, 그건 이 MR 이 아니라 앱이 언어를 올려 주는
 * 일이 먼저다. 지금은 한국어로 보낸다.
 */
@Component
@Profile({ "db", "dev" })
public class TripPushNotifier {

	private static final Logger log = LoggerFactory.getLogger(TripPushNotifier.class);

	private final ItineraryRepository itineraryRepository;

	private final TripRepository tripRepository;

	private final ActorNames actorNames;

	private final PushTokenService pushTokens;

	private final PushSender pushSender;

	public TripPushNotifier(ItineraryRepository itineraryRepository, TripRepository tripRepository,
			ActorNames actorNames, PushTokenService pushTokens, PushSender pushSender) {
		this.itineraryRepository = itineraryRepository;
		this.tripRepository = tripRepository;
		this.actorNames = actorNames;
		this.pushTokens = pushTokens;
		this.pushSender = pushSender;
	}

	/**
	 * 일정에 새 판이 붙었다.
	 *
	 * <p>🔴 <b>만든 사람에게 보낼 것인가가 {@code CREATE} 에서만 뒤집힌다.</b> 순서를 바꾼 사람은
	 * 방금 자기 손으로 눌렀고 화면을 보고 있다 — 거기에 알림을 띄우면 그냥 방해다. 그런데 일정
	 * 생성은 <b>오래 걸려서 사람이 앱을 닫는다.</b> 기다리던 본인이야말로 받아야 할 사람이다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onItineraryChanged(ItineraryChangedByMember event) {
		try {
			Itinerary itinerary = this.itineraryRepository.findById(event.itineraryId()).orElse(null);
			if (itinerary == null) {
				log.warn("알림을 보낼 일정이 없습니다. itineraryId={}", event.itineraryId());
				return;
			}
			Trip trip = this.tripRepository.findById(itinerary.tripId()).orElse(null);
			if (trip == null) {
				log.warn("알림을 보낼 여행이 없습니다. tripId={}", itinerary.tripId());
				return;
			}

			boolean toActorToo = event.operation() == ItineraryVersion.Operation.CREATE;
			List<String> recipients = this.tripRepository.findMembers(trip.tripId()).stream()
					.map(TripMember::userId)
					.filter((userId) -> toActorToo || !Objects.equals(userId, event.actorUserId()))
					.toList();

			// CREATE 문구에는 「누가」가 없다. 그래서 만든 본인에게도 같은 한 통을 보낼 수 있다 —
			// 다른 갈래는 위에서 본인을 이미 뺐으므로 이름이 늘 남의 이름이다.
			String actorName = this.actorNames.resolve(List.of(event.actorUserId() == null ? "" : event.actorUserId()))
					.get(event.actorUserId());

			String href = "/trips/" + event.itineraryId() + "/itinerary";
			push(recipients, new PushMessage(
					title(event.operation()),
					itineraryBody(trip.displayTitle(), event.operation(), actorName),
					href, event.itineraryId()));
		}
		catch (RuntimeException exception) {
			log.error("일정 변경 알림을 보내지 못했습니다. 편집 자체는 이미 끝났으므로 되돌리지 않습니다. itineraryId={}",
					event.itineraryId(), exception);
		}
	}

	/** 동행이 들어왔다 — 들어온 본인 말고 원래 있던 사람들에게. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMemberJoined(TripMemberJoined event) {
		try {
			Trip trip = this.tripRepository.findById(event.tripId()).orElse(null);
			if (trip == null) {
				log.warn("알림을 보낼 여행이 없습니다. tripId={}", event.tripId());
				return;
			}
			List<String> recipients = this.tripRepository.findMembers(event.tripId()).stream()
					.map(TripMember::userId)
					.filter((userId) -> !Objects.equals(userId, event.joinedUserId()))
					.toList();

			String joinedName = this.actorNames.resolve(List.of(event.joinedUserId())).get(event.joinedUserId());
			push(recipients, new PushMessage("동행이 합류했어요",
					personBody(trip.displayTitle(), joinedName, "함께하기로 했어요."),
					collaborateHref(event.tripId()), null));
		}
		catch (RuntimeException exception) {
			log.error("동행 합류 알림을 보내지 못했습니다. tripId={}", event.tripId(), exception);
		}
	}

	/**
	 * 자격이 바뀌었다 — <b>당사자에게만.</b>
	 *
	 * <p>방금까지 되던 편집이 왜 안 되는지는 그 사람만 알면 된다. 남들에게는 「누가 누구를
	 * 강등했다」는 소식일 뿐이고, 그건 알림으로 보낼 말이 아니다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRoleChanged(TripMemberRoleChanged event) {
		try {
			Trip trip = this.tripRepository.findById(event.tripId()).orElse(null);
			if (trip == null) {
				log.warn("알림을 보낼 여행이 없습니다. tripId={}", event.tripId());
				return;
			}
			String actorName = this.actorNames.resolve(List.of(event.actorUserId() == null ? "" : event.actorUserId()))
					.get(event.actorUserId());

			String title = (event.newRole() == TripMember.Role.EDITOR)
					? "이제 일정을 함께 고칠 수 있어요"
					: "이제 일정을 보기만 할 수 있어요";
			push(List.of(event.targetUserId()), new PushMessage(title,
					personBody(trip.displayTitle(), actorName, "역할을 바꿨어요."),
					collaborateHref(event.tripId()), null));
		}
		catch (RuntimeException exception) {
			log.error("역할 변경 알림을 보내지 못했습니다. tripId={}", event.tripId(), exception);
		}
	}

	/**
	 * 참여자·역할 화면. 여행 번호가 그대로 주소가 된다 — 앱의 {@code (trip)} 은 폴더를 묶기만 하는
	 * 이름이라 주소에 안 들어간다({@code frontend/app/(trip)/[id]/collaborate.tsx},
	 * {@code router.push(`/${id}/collaborate`)}).
	 */
	private static String collaborateHref(String tripId) {
		return "/" + tripId + "/collaborate";
	}

	private void push(List<String> recipientUserIds, PushMessage message) {
		if (recipientUserIds.isEmpty()) {
			return;
		}
		List<String> tokens = this.pushTokens.tokensOf(recipientUserIds);
		if (tokens.isEmpty()) {
			// 흔한 일이다 — 알림을 켠 사람이 아직 없다. 로그를 남기면 이 줄만 쌓인다.
			return;
		}
		List<String> gone = this.pushSender.send(tokens, message);
		this.pushTokens.forget(gone);
	}

	/**
	 * 「무엇이」 — 앱 알림 목록의 제목과 같은 말
	 * ({@code activityFeed.ts} 의 {@code noticeCopy}).
	 *
	 * <p>{@code default} 를 두지 않는다. 판 종류가 늘면 <b>여기가 컴파일 오류로 막힌다</b> —
	 * 두면 새 종류가 조용히 「일정이 바뀌었어요」로 뭉개지고, 아무도 못 알아챈다.
	 */
	static String title(ItineraryVersion.Operation operation) {
		return switch (operation) {
			case CREATE -> "일정이 만들어졌어요";
			case REGENERATE, REGENERATE_DAY, REPLAN_DAY -> "남은 일정을 다시 계획했어요";
			case REVERT -> "변경을 되돌렸어요";
			case REORDER -> "일정 순서가 바뀌었어요";
			case LOCK_ITEM -> "장소가 고정됐어요";
			case REMOVE_ITEM -> "장소가 빠졌어요";
			case ADD_ITEM, REPLACE_ITEM -> "장소가 더해졌어요";
		};
	}

	/** 「누가」 뒤에 붙는 말. {@code CREATE} 만 사람이 아니라 부탁하는 말이다. */
	static String phrase(ItineraryVersion.Operation operation) {
		return switch (operation) {
			case CREATE -> "확인하고 저장해 주세요.";
			case REGENERATE, REGENERATE_DAY, REPLAN_DAY -> "다시 계획했어요.";
			case REVERT -> "되돌렸어요.";
			case REORDER -> "순서를 바꿨어요.";
			case LOCK_ITEM -> "장소를 고정했어요.";
			case REMOVE_ITEM -> "장소를 뺐어요.";
			case ADD_ITEM, REPLACE_ITEM -> "장소를 더했어요.";
		};
	}

	/**
	 * 「「부산 바다 2박 3일」 — 수민님이 순서를 바꿨어요.」
	 *
	 * <p>이름이 없으면(탈퇴해서 사람 행이 없다) 여행 이름만 남긴다. 앱의 {@code noticeCopy} 가
	 * {@code who} 가 없을 때 하는 것과 같다 — 서버가 「누군가」 같은 말을 지어내지 않는다.
	 *
	 * <p>{@code CREATE} 만 예외다. 「확인하고 저장해 주세요」는 <b>사람이 없어도 뜻이 통하는 말</b>이라
	 * 이름과 무관하게 붙는다. 실제로 CREATE 는 만든 본인에게도 가므로 이름을 넣으면 자기 이름이 뜬다.
	 */
	static String itineraryBody(String tripTitle, ItineraryVersion.Operation operation, String actorName) {
		String head = "「" + tripTitle + "」";
		if (operation == ItineraryVersion.Operation.CREATE) {
			return head + " — " + phrase(operation);
		}
		return personBody(tripTitle, actorName, phrase(operation));
	}

	/** 「「부산 바다 2박 3일」 — 수민님이 함께하기로 했어요.」 이름이 없으면 여행 이름만. */
	static String personBody(String tripTitle, String actorName, String phrase) {
		String head = "「" + tripTitle + "」";
		if (actorName == null || actorName.isBlank()) {
			return head;
		}
		return head + " — " + actorName + "님이 " + phrase;
	}
}
