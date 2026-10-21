package com.gabolle.backend.appversion.adapter;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gabolle.backend.appversion.application.AppReleasePort;
import com.gabolle.backend.appversion.domain.AppRelease;

/**
 * {@code app_release} 표를 매 요청 읽는다. 앱이 하루 한 번만 부르므로 캐시를 두지 않는다 —
 * 캐시가 없어야 표를 고친 즉시 반영된다. 바꾸는 법은 V20261011100000__app_release.sql 머리말에 있다.
 */
@Repository
@Profile({ "db", "dev" })
public class JdbcAppReleaseRepository implements AppReleasePort {

	private final JdbcTemplate jdbcTemplate;

	public JdbcAppReleaseRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<AppRelease> find(String platform) {
		return this.jdbcTemplate.query(
				"SELECT platform, latest_version, min_supported_version, store_url FROM app_release WHERE platform = ?",
				(rs, i) -> new AppRelease(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
				platform).stream().findFirst();
	}
}
