package com.gabolle.backend.feedback.adapter;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.feedback.application.FeedbackNotifier;
import com.gabolle.backend.notification.application.PushCopy;
import com.gabolle.backend.notification.application.PushMessage;
import com.gabolle.backend.notification.application.PushSender;
import com.gabolle.backend.notification.application.PushTokenService;

/** 의견 답변 알림 — S15P21E201-2049. 문구는 받는 사람의 앱 언어(여섯)로. 답변 본문은 알림에 싣지 않는다. */
@Component
@Profile({ "db", "dev" })
public class PushFeedbackNotifier implements FeedbackNotifier {

	private static final Logger log = LoggerFactory.getLogger(PushFeedbackNotifier.class);

	private final PushTokenService pushTokens;

	private final PushSender pushSender;

	public PushFeedbackNotifier(PushTokenService pushTokens, PushSender pushSender) {
		this.pushTokens = pushTokens;
		this.pushSender = pushSender;
	}

	@Override
	public void replied(UUID userId, String language) {
		try {
			List<String> tokens = this.pushTokens.tokensOf(List.of(userId.toString()));
			if (tokens.isEmpty()) {
				return;
			}
			PushCopy.Lang lang = PushCopy.lang(language);
			List<String> dead = this.pushSender.send(tokens, new PushMessage(title(lang), body(lang), "/feedback", null));
			if (!dead.isEmpty()) {
				this.pushTokens.forget(dead);
			}
		}
		catch (RuntimeException e) {
			log.warn("의견 답변 알림을 못 보냈다: user={}", userId, e);
		}
	}

	static String title(PushCopy.Lang lang) {
		return switch (lang) {
			case EN -> "We've answered your feedback";
			case JA -> "ご意見への回答が届きました";
			case ZH_HANS -> "您的意见已有回复";
			case ZH_HANT -> "您的意見已有回覆";
			case VI -> "Góp ý của bạn đã được phản hồi";
			default -> "보내 주신 의견에 답변했어요";
		};
	}

	static String body(PushCopy.Lang lang) {
		return switch (lang) {
			case EN -> "Thank you. Your report has been handled.";
			case JA -> "ありがとうございます。お送りいただいた内容を対応しました。";
			case ZH_HANS -> "谢谢您！您反馈的问题已处理完毕。";
			case ZH_HANT -> "謝謝您！您回報的問題已處理完畢。";
			case VI -> "Cảm ơn bạn! Nội dung bạn gửi đã được xử lý.";
			default -> "고맙습니다. 보내 주신 내용을 처리했어요.";
		};
	}
}
