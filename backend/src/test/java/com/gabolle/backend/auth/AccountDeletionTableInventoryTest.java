package com.gabolle.backend.auth;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code app_user} 를 가리키는 표가 새로 생기면 여기가 빨개진다.
 *
 * <p>아래 목록은 「이 표는 탈퇴 때 지워야 한다」를 주장하지 않는다. {@code app_user} 를
 * 가리키는 표를 그대로 찍어 둔 것이고, 요구하는 것은 표를 새로 만들 때 탈퇴를 한 번
 * 생각했다는 표시뿐이다. 실제로 무엇이 지워지는지는 {@code AccountDeletionService} 와
 * {@code AccountDeletionIntegrationTest} 가 정한다.
 *
 * <p>빨개지는 경우는 둘이고 둘 다 정상이다 — 표를 새로 만들었으면 목록에 한 줄 더하고,
 * 외래키를 없앴으면 한 줄 뺀다.
 *
 * <p>Postgres 가 없으면 건너뛰므로 실제 판정은 CI 에서 난다.
 */
class AccountDeletionTableInventoryTest extends AuthPostgresIntegrationTest {

	/** DB 에서 그대로 읽은 것. 주장이 아니다 — 위 머리말 참고. */
	private static final List<String> TABLES_POINTING_AT_APP_USER = List.of(
			"auth_identity", "auth_session", "collection",
			// 음식 그림 만든 횟수. dish_image·dish_description 은 사람을 안 가리켜 여기 없다.
			"dish_image_usage",
			"feed_build",
			// itinerary_versions·itinerary_excluded_place·recommendation_place_action 은 V20261002160000 이
			// 외래키를 뗐다 — 비회원(익명 세션)도 일정을 만들고 고친다. 탈퇴는 app_user 행을 지우지 않아 영향이 없다.
			"local_credential", "menu_scan_usage", "oauth_signup_ticket", "place_review",
			"place_visit_verification",
			// 기기 푸시 토큰. 탈퇴 때 AccountDeletionService 가 직접 지운다 — CASCADE 는 안 돈다
			// (app_user 행을 익명화만 하므로). 안 지우면 탈퇴한 사람 폰에 알림이 계속 간다.
			"push_token",
			"saved_place", "story", "story_coauthor",
			"story_invite", "story_link_copy", "story_reaction",
			"story_save", "story_view", "trip_invite", "trip_member",
			"trip_share_link", "uploaded_image",
			// 올라간 동영상. uploaded_image 와 같은 자리에서 같은 순서로 지워진다 —
			// story_video 를 먼저 지우지 않으면 외래키 위반으로 탈퇴 전체가 실패한다.
			"uploaded_video",
			"user_block", "user_consent", "user_follow", "user_pace_factor",
			// (사람, 장소) 의 취향 반영 상태. 탈퇴 때 AccountDeletionService 가 직접 지운다 —
			// CASCADE 는 안 돈다(계정 행을 익명화만 하므로). 안 지우면 탈퇴한 사람의 행동
			// 이력이 그대로 남는다 (S15P21E201-1500).
			"user_place_taste_state",
			"user_taste_vector",
			"user_travel_constraint");

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("🔴 app_user 를 가리키는 표 목록이 그대로다 — 달라졌으면 탈퇴 때 어떻게 할지 정하라는 뜻이다")
	void everyTablePointingAtAppUserIsAccountedFor() {
		List<String> actual = this.jdbc.queryForList("""
				SELECT DISTINCT child.relname
				  FROM pg_constraint c
				  JOIN pg_class child ON child.oid = c.conrelid
				  JOIN pg_class parent ON parent.oid = c.confrelid
				 WHERE c.contype = 'f'
				   AND parent.relname = 'app_user'
				   AND parent.relnamespace = child.relnamespace
				 ORDER BY 1
				""", String.class);

		List<String> appeared = new ArrayList<>(actual);
		appeared.removeAll(TABLES_POINTING_AT_APP_USER);
		List<String> vanished = new ArrayList<>(TABLES_POINTING_AT_APP_USER);
		vanished.removeAll(actual);

		assertThat(appeared).as("""

				🔴 app_user 를 가리키는 표가 새로 생겼습니다: %s

				   탈퇴할 때 이 표는 어떻게 합니까? 셋 중 하나를 정하십시오.
					 (가) AccountDeletionService 에서 지운다
					 (나) 사람을 가리키는 칸을 비운다 (ON DELETE SET NULL 등)
					 (다) 일부러 남긴다 — 그 이유를 표 주석에 적는다

				   정한 뒤 이 파일의 TABLES_POINTING_AT_APP_USER 에 한 줄 더하십시오.
				   🔴 목록에 더하는 것 자체는 아무것도 지우지 않습니다. 그건 (가) 를 골랐을 때
					  AccountDeletionService 를 고쳐야 하는 별개의 일입니다.
				""".formatted(appeared)).isEmpty();

		assertThat(vanished).as("""

				외래키가 없어진 표입니다: %s

				   의도한 것이면 이 파일의 TABLES_POINTING_AT_APP_USER 에서 그 줄을 빼십시오.
				   의도하지 않았다면 마이그레이션이 실수로 제약을 떨어뜨린 것입니다.
				""".formatted(vanished)).isEmpty();
	}
}
