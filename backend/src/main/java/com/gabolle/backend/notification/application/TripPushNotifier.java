package com.gabolle.backend.notification.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

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
 * <p>🔴 <b>작성자가 없을 수 있다 — {@code Collections.singletonList} 여야 한다
 * (S15P21E201-1484).</b> 판 이력의 작성자 칸은 탈퇴하면 비워지는 자리다
 * ({@code ON DELETE SET NULL}). {@link ActorNames#resolve} 는 <b>그 {@code null} 을 스스로
 * 걸러 주도록 만들어져 있으므로 값을 그대로 넘기면 된다.</b> 전에는 {@code null} 을 빈
 * 문자열로 바꿔 넘겼는데, 빈 문자열은 「없음」이 아니라 「값」이라 걸러지지 않고
 * {@code UUID.fromString("")} 까지 가서 터졌다. 그 예외를 아래의 {@code catch} 가 먹어
 * <b>그 변경에 대한 알림이 한 통도 안 나갔다.</b> {@code List.of} 를 쓸 수 없는 것도 같은
 * 이유다 — {@code null} 을 담으면 그 자리에서 던진다.
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
			String actorName = this.actorNames.resolve(Collections.singletonList(event.actorUserId()))
					.get(event.actorUserId());

			String href = "/trips/" + event.itineraryId() + "/itinerary";
			boolean created = event.operation() == ItineraryVersion.Operation.CREATE;
			// 받는 사람마다 그 사람 앱 언어로(S15P21E201-1864). 문구는 PushCopy 한 곳에 있다.
			pushByLanguage(recipients, (lang) -> {
				String label = PushCopy.tripLabel(trip, lang);
				return created
						? new PushMessage(PushCopy.createdTitle(lang), PushCopy.createdBody(label, lang), href, event.itineraryId())
						: new PushMessage(PushCopy.editTitle(event.operation(), actorName, lang), PushCopy.editBody(label, lang),
								href, event.itineraryId());
			});
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
			pushByLanguage(recipients, (lang) -> new PushMessage(PushCopy.joinedTitle(joinedName, lang),
					PushCopy.tripLabel(trip, lang), collaborateHref(event.tripId()), null));
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
			String actorName = this.actorNames.resolve(Collections.singletonList(event.actorUserId()))
					.get(event.actorUserId());

			boolean canEdit = event.newRole() == TripMember.Role.EDITOR;
			pushByLanguage(List.of(event.targetUserId()), (lang) -> new PushMessage(PushCopy.roleTitle(canEdit, lang),
					PushCopy.roleBody(PushCopy.tripLabel(trip, lang), actorName, lang), collaborateHref(event.tripId()), null));
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

	/** 받는 사람을 앱 언어별로 나눠 언어마다 한 번씩 보낸다. 언어를 모르면 한국어다. */
	private void pushByLanguage(List<String> recipientUserIds, Function<PushCopy.Lang, PushMessage> messageFor) {
		if (recipientUserIds.isEmpty()) {
			return;
		}
		Map<String, String> languages = this.actorNames.languagesOf(recipientUserIds);
		Map<PushCopy.Lang, List<String>> byLang = new EnumMap<>(PushCopy.Lang.class);
		for (String userId : recipientUserIds) {
			byLang.computeIfAbsent(PushCopy.lang(languages == null ? null : languages.get(userId)), (key) -> new ArrayList<>())
					.add(userId);
		}
		byLang.forEach((lang, users) -> push(users, messageFor.apply(lang)));
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
}
