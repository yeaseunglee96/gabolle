package com.gabolle.backend.story.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.story.domain.UploadedImage;

public interface UploadedImageRepository extends JpaRepository<UploadedImage, UUID> {

	Optional<UploadedImage> findByStorageKey(String storageKey);

	/** 기록 저장 요청이 준 주소들을 한 번에 찾는다. 없는 주소·남의 주소는 호출자가 걸러 400 으로 답한다. */
	List<UploadedImage> findByImageUrlIn(Collection<String> imageUrls);

	List<UploadedImage> findByUploadedImageIdIn(Collection<UUID> ids);

	/**
	 * 어디에도 안 붙은 채 오래된 사진 — S15P21E201-1284.
	 *
	 * <h2>🔴 붙는 자리가 <b>둘</b>이다. 하나만 보면 썸네일을 전부 지운다</h2>
	 *
	 * 자연스러운 질의는 {@code story_image} 만 보는 것이다. 그런데 <b>동영상 썸네일은 거기
	 * 안 들어간다</b> — 「한 기록에 사진 3장」 한 칸을 안 먹게 하려고 일부러 그렇게 뒀다
	 * (S15P21E201-1275 · -1279). 그래서 {@code story_image} 만 보면 <b>붙어 있는 동영상의
	 * 썸네일이 전부 고아로 보이고</b>, 글이 멀쩡히 살아 있는데도 지워진다.
	 *
	 * <p>그러면 화면에는 <b>검은 칸만 남고 아무 오류도 안 난다.</b> 몇 주 뒤 사람이 눈으로
	 * 볼 때까지 아무도 모른다. 그래서 {@code story_video.thumbnailUploadId} 도 함께 본다.
	 *
	 * <h2>🔴 2026-09-19 — 붙는 자리가 <b>넷</b>이 됐다 (S15P21E201-1297)</h2>
	 *
	 * 계정에도 사진이 붙는다 — <b>프로필 사진</b>({@code app_user.avatar_url})과 <b>커버 사진</b>
	 * ({@code app_user.cover_url}). 둘 다 같은 업로드 창구로 올라오므로 {@code uploaded_image}
	 * 행이 되는데, {@code story_image} 에도 {@code story_video} 에도 없다.
	 *
	 * <p>🔴 <b>프로필 사진은 이 질의가 생기기 전부터 그 상태였다</b>(S15P21E201-844 가 먼저 있었다).
	 * 청소기가 아직 「세기만 하는 모드」({@code enabled=false})라 아무도 안 잃었을 뿐이고,
	 * <b>켜는 날 유예 기간이 지난 프로필 사진이 전부 사라졌을 것</b>이다. 커버 사진을 만들면서
	 * 같은 구멍을 하나 더 파는 대신 두 자리를 함께 막는다.
	 *
	 * <p>🔴 이쪽은 <b>주소로 잇는다.</b> 계정은 업로드 식별자가 아니라 <b>주소 문자열</b>을
	 * 들고 있기 때문이다({@code AuthUserResponse} 가 그 주소를 그대로 화면에 준다). 청소는
	 * 하루 한 번 한 판 상한 안에서만 돌아 이 비교가 비용이 되지 않는다 — 계정 수가 크게 늘면
	 * 그때 {@code avatar_url}·{@code cover_url} 에 인덱스를 둔다.
	 *
	 * <p>🔴 <b>앞으로 자리가 하나 더 생기면 여기도 같이 늘려야 한다.</b> 안 늘리면 그 자리에
	 * 붙은 파일이 조용히 지워진다 — 지금 이 절이 생긴 이유가 그것이다.
	 *
	 * <p>🔴 <b>이미 지운 것({@code deletedAt} 이 찬 것)은 다시 안 본다.</b> 파일은 이미 없고,
	 * 다시 지우려 들면 뒷정리 대기열만 더럽힌다.
	 *
	 * @param cutoff 이 시각보다 <b>먼저</b> 올라온 것만. 방금 올리고 아직 글을 쓰는 중인 것을
	 * 지우지 않기 위한 유예다
	 * @param limit 한 번에 가져올 상한. 한 판에 무한정 지우지 않는다 — 이상한 일이 생겨도
	 * 피해가 한 판 크기로 묶인다
	 */
	@Query("""
			SELECT i FROM UploadedImage i
			 WHERE i.createdAt < :cutoff
			   AND i.deletedAt IS NULL
			   AND NOT EXISTS (SELECT 1 FROM StoryImage si WHERE si.uploadedImageId = i.uploadedImageId)
			   AND NOT EXISTS (SELECT 1 FROM StoryVideo sv WHERE sv.thumbnailUploadId = i.uploadedImageId)
			   AND NOT EXISTS (SELECT 1 FROM AppUser u
			                    WHERE u.avatarUrl = i.imageUrl OR u.coverUrl = i.imageUrl)
			 ORDER BY i.createdAt ASC
			""")
	List<UploadedImage> findOrphansOlderThan(@Param("cutoff") Instant cutoff, Pageable limit);
}
