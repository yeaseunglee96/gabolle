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
 * S15P21E201-1196 — {@code app_user} 를 가리키는 표가 <b>새로 생기면</b> 여기가 빨개진다.
 *
 * <h2>왜 필요한가</h2>
 *
 * {@code AccountDeletionIntegrationTest} 는 상황 열일곱 가지를 잰다. 좋은 검사지만 <b>표 이름을
 * 목록으로 갖고 있지 않다.</b> 그래서 {@code app_user} 를 가리키는 표를 새로 만들고 탈퇴 처리에
 * 안 넣어도 <b>전부 초록이다.</b>
 *
 * <p>이 저장소는 그 구멍에 이미 한 번 빠졌다 — 인계 문서가 <i>「🔴 새 표는 처음부터 탈퇴 삭제
 * 목록에 넣는다. 오늘 표 열 개를 뒤늦게 메운 자리다」</i> 라고 적어 뒀다. <b>사람이 매번 기억해야
 * 하는 것은 반드시 또 빠진다.</b>
 *
 * <h2>🔴 아래 목록은 주장이 아니라 사진이다</h2>
 *
 * 이 목록은 <b>「이 표는 탈퇴 때 지워야 한다」를 하나도 주장하지 않는다.</b> 2026-09-18 현재
 * {@code app_user} 를 가리키고 있는 표를 <b>그대로 찍어 둔 것</b>뿐이다.
 *
 * <p>그래서 <b>「무엇이 고의로 안 지워지는가」를 아직 몰라도</b> 이 검사를 둘 수 있다. 그 질문은
 * 답이 없는 채로 남아 있고, 이 검사는 그 답을 요구하지 않는다. 요구하는 것은 하나다 —
 * <b>표를 새로 만들 때 탈퇴를 한 번 생각했다는 표시.</b>
 *
 * <p>🔴 <b>여기 이름이 있다고 「지워도 되는 표」로 읽지 마라.</b> 실제로 탈퇴 때 무엇이 지워지는지는
 * {@code AccountDeletionService} 와 {@code AccountDeletionIntegrationTest} 가 정한다.
 *
 * <h2>이 검사가 빨개질 두 가지 경우 — 둘 다 정상이다</h2>
 *
 * <ol>
 *   <li><b>표를 새로 만들었다.</b> 목록에 한 줄 더하면 된다. 더하기 전에 탈퇴 때 그 표를 어떻게
 *       할지 정한다 — 그것이 이 검사가 하는 일 전부다</li>
 *   <li><b>외래키를 없앴다.</b> 목록에서 한 줄 빼면 된다</li>
 * </ol>
 *
 * <p>🔴 <b>미리 알린다 — 계정 행 하드 삭제 작업이 들어오면 이 검사가 빨개진다.</b> 그 작업은
 * {@code itinerary_versions} 의 외래키를 {@code ON DELETE SET NULL} 로 다시 만드는 것을 포함하고,
 * 그밖에 「규칙 없음」인 외래키들을 손대게 된다. <b>그건 의도한 것이고 목록을 고치면 끝난다.</b>
 * 예고 없이 남의 MR 이 빨개지면 그 사람은 검사를 적으로 여기므로 여기 적어 둔다.
 *
 * <h2>DB 가 없으면 건너뛴다</h2>
 *
 * {@code AuthPostgresIntegrationTest} 를 상속하므로 {@code PostgresAvailableCondition} 이 그대로
 * 붙는다. 🔴 <b>그래서 이 검사의 진짜 판정은 CI 다</b> — 도커가 꺼진 PC 에서는 건너뛴 채로 초록이다.
 */
class AccountDeletionTableInventoryTest extends AuthPostgresIntegrationTest {

	/** 2026-09-18 에 DB 에서 그대로 읽은 것. 주장이 아니다 — 위 머리말 참고. */
	private static final List<String> TABLES_POINTING_AT_APP_USER = List.of(
			"auth_identity", "auth_session", "collection", "feed_build", "itinerary_excluded_place",
			"itinerary_versions", "local_credential", "menu_scan_usage", "oauth_signup_ticket", "place_review",
			"place_visit_verification", "recommendation_place_action", "saved_place", "story", "story_coauthor",
			"story_invite", "story_link_copy", "story_reaction",
			// 🔴 S15P21E201-1227 — 글 저장·북마크. (가) AccountDeletionService.USER_OWNED_ROWS 에서 지운다.
			"story_save", "story_view", "trip_invite", "trip_member",
			"trip_share_link", "uploaded_image",
			// 🔴 S15P21E201-1275 — 올라간 동영상. (가) AccountDeletionService.deleteUploadedFiles 에서
			//    지운다. uploaded_image 와 같은 자리에서 같은 순서로 지워진다 — story_video 를 먼저
			//    지우지 않으면 외래키 위반으로 탈퇴 전체가 실패한다(AccountDeletionVideoFilesTest 가 잰다).
			"uploaded_video",
			"user_block", "user_consent", "user_follow", "user_pace_factor", "user_taste_vector",
			// 🔴 S15P21E201-1231 — 여행 조건 모달의 답. 새 표라 처음부터 여기 넣는다.
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
