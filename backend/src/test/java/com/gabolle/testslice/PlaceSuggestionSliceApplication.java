package com.gabolle.testslice;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.gabolle.backend.placesuggestion.application.ApprovalNotifier;

/**
 * 현지인 장소 제안(S15P21E201-2031)을 진짜 PostgreSQL 위에서 보는 슬라이스.
 *
 * <p>🔴 {@code placesuggestion.adapter} 를 훑지 않는다. 거기 있는 푸시 알림 구현이 {@code notification} 의 토큰
 * 저장소를 끌고 오기 때문이다. 대신 무엇을 알렸는지 적어 두는 {@link RecordingNotifier} 를 올린다 — 시험은 「승인하면
 * 제안한 사람에게 알림이 간다」를 이것으로 잰다.
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.placesuggestion.application",
		"com.gabolle.backend.placesuggestion.presentation"
})
public class PlaceSuggestionSliceApplication {

	@Bean
	RecordingNotifier recordingNotifier() {
		return new RecordingNotifier();
	}

	/** 승인 알림을 보내지 않고 적어 둔다. */
	public static class RecordingNotifier implements ApprovalNotifier {

		public final List<UUID> approvedSuggesters = new ArrayList<>();

		@Override
		public void approved(UUID suggesterUserId, String language, String placeName, UUID placeId) {
			this.approvedSuggesters.add(suggesterUserId);
		}
	}
}
