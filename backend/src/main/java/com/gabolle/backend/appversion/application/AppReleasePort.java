package com.gabolle.backend.appversion.application;

import java.util.Optional;

import com.gabolle.backend.appversion.domain.AppRelease;

/** 플랫폼별 배포 정보를 읽는다. 값은 코드가 아니라 {@code app_release} 표에 있다 — 배포 없이 바꾸기 위해서다. */
@FunctionalInterface
public interface AppReleasePort {

	Optional<AppRelease> find(String platform);
}
