package com.gabolle.backend.placesuggestion.adapter;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.notification.application.PushCopy;
import com.gabolle.backend.notification.application.PushMessage;
import com.gabolle.backend.notification.application.PushSender;
import com.gabolle.backend.notification.application.PushTokenService;
import com.gabolle.backend.placesuggestion.application.ApprovalNotifier;

/**
 * 제안 승인을 폰 알림으로 — S15P21E201-2031. 문구는 받는 사람의 앱 언어(여섯)로, 누르면 그 장소 화면이 열린다.
 */
@Component
@Profile({ "db", "dev" })
public class PushApprovalNotifier implements ApprovalNotifier {

	private static final Logger log = LoggerFactory.getLogger(PushApprovalNotifier.class);

	private final PushTokenService pushTokens;

	private final PushSender pushSender;

	public PushApprovalNotifier(PushTokenService pushTokens, PushSender pushSender) {
		this.pushTokens = pushTokens;
		this.pushSender = pushSender;
	}

	@Override
	public void approved(UUID suggesterUserId, String language, String placeName, UUID placeId) {
		try {
			List<String> tokens = this.pushTokens.tokensOf(List.of(suggesterUserId.toString()));
			if (tokens.isEmpty()) {
				return;
			}
			PushCopy.Lang lang = PushCopy.lang(language);
			PushMessage message = new PushMessage(title(lang), body(placeName, lang), "/place/" + placeId, null);
			List<String> dead = this.pushSender.send(tokens, message);
			if (!dead.isEmpty()) {
				this.pushTokens.forget(dead);
			}
		}
		catch (RuntimeException e) {
			log.warn("장소 제안 승인 알림을 못 보냈다: suggester={}", suggesterUserId, e);
		}
	}

	static String title(PushCopy.Lang lang) {
		return switch (lang) {
			case EN -> "Your place suggestion was approved";
			case JA -> "提案した場所が承認されました";
			case ZH_HANS -> "您推荐的地点已通过审核";
			case ZH_HANT -> "您推薦的地點已通過審核";
			case VI -> "Địa điểm bạn đề xuất đã được duyệt";
			default -> "제안한 장소가 승인됐어요";
		};
	}

	static String body(String placeName, PushCopy.Lang lang) {
		return switch (lang) {
			case EN -> placeName + " now shows a \"Local pick\" badge.";
			case JA -> placeName + " に「地元のおすすめ」が付きました。";
			case ZH_HANS -> placeName + " 已标上「本地人推荐」。";
			case ZH_HANT -> placeName + " 已標上「在地人推薦」。";
			case VI -> placeName + " đã có nhãn \"Người địa phương gợi ý\".";
			default -> placeName + "에 「현지인 추천」이 붙었어요.";
		};
	}
}
