package com.gabolle.backend.place.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 장소의 대표 사진 밖의 사진 한 장(S15P21E201-1840). 읽기만 한다 — 적재는 사람이 순서를 정해 돌리는
 * 배치가 SQL 로 한다. 출처를 밝히면 영구히 쓸 수 있는 사진만 들어온다(공공누리 제1유형 등).
 */
@Entity
@Table(name = "place_photo")
public class PlacePhoto {

	@Id
	@Column(name = "photo_id")
	private Long photoId;

	@Column(name = "place_id", nullable = false)
	private UUID placeId;

	/** 1부터. 화면은 이 순서대로 대표 사진 뒤에 붙인다. */
	@Column(name = "position", nullable = false)
	private int position;

	@Column(name = "url", nullable = false, length = 500)
	private String url;

	/** 사진 옆에 그대로 보여 줄 출처 문구. 예: {@code 출처 : 부산관광아카이브} */
	@Column(name = "source", nullable = false, length = 100)
	private String source;

	@Column(name = "license_name", length = 100)
	private String licenseName;

	@Column(name = "license_url", length = 500)
	private String licenseUrl;

	@Column(name = "file_page", length = 500)
	private String filePage;

	protected PlacePhoto() {
	}

	public UUID getPlaceId() { return this.placeId; }
	public int getPosition() { return this.position; }
	public String getUrl() { return this.url; }
	public String getSource() { return this.source; }

	/** 라이선스 이름이 없으면 {@code null} — {@link Place.PhotoLicense} 는 이름 없이 만들지 않는다. */
	public Place.PhotoLicense getLicense() {
		return (this.licenseName == null || this.licenseName.isBlank()) ? null
				: new Place.PhotoLicense(this.licenseName, this.licenseUrl, this.filePage);
	}
}
