package com.gabolle.backend.notification.application;

import java.time.LocalDate;
import java.util.Locale;

import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.trip.domain.Trip;

/**
 * 폰 알림 문구 — 받는 사람의 앱 언어로(S15P21E201-1864, UI 개선 캔버스 ⑰).
 *
 * <p>🔴 전에는 문구가 한국어로 박혀 있어 영어·일본어·중국어로 앱을 쓰는 사람도 한국어 알림을 받았고, 제목은
 * 「장소가 빠졌어요」처럼 누가 했는지가 본문 끝에 있었고, 이름 없는 여행은 「2026-09-25 ~ 2026-09-27」 원문이었다.
 * 이제 제목이 누가·무엇을, 본문이 어느 여행인지를 말한다.
 *
 * <p>여행 이름이 없으면 날짜로 부른다 — 이름을 지어내지 않는 규칙(S15P21E201-1738, 앱 내 여행 목록과 같다).
 * 언어 코드는 앱의 것({@code ko} · {@code en} · {@code ja} · {@code zh-Hans} · {@code zh-Hant})이고, 모르면 한국어다.
 */
public final class PushCopy {

	public enum Lang { KO, EN, JA, ZH_HANS, ZH_HANT }

	private PushCopy() {
	}

	/** 사용자 표의 언어 값을 다섯 중 하나로. 비었거나 모르는 값이면 한국어. */
	public static Lang lang(String raw) {
		if (raw == null || raw.isBlank()) {
			return Lang.KO;
		}
		String value = raw.trim().toLowerCase(Locale.ROOT);
		if (value.startsWith("zh")) {
			return (value.contains("hant") || value.contains("tw") || value.contains("hk")) ? Lang.ZH_HANT : Lang.ZH_HANS;
		}
		if (value.startsWith("ja")) {
			return Lang.JA;
		}
		if (value.startsWith("en")) {
			return Lang.EN;
		}
		return Lang.KO;
	}

	/** 여행을 부르는 말 — 사람이 붙인 이름, 없으면 「10월 1일 – 3일」. */
	public static String tripLabel(Trip trip, Lang lang) {
		String named = trip.title();
		if (named != null && !named.isBlank()) {
			return named.trim();
		}
		return dates(trip.startDate(), trip.finishDate(), lang);
	}

	public static String dates(LocalDate start, LocalDate end, Lang lang) {
		if (start == null) {
			return "";
		}
		String from = day(start, lang, true);
		if (end == null || end.equals(start)) {
			return from;
		}
		boolean sameMonth = end.getYear() == start.getYear() && end.getMonthValue() == start.getMonthValue();
		return from + " – " + day(end, lang, !sameMonth);
	}

	private static String day(LocalDate date, Lang lang, boolean withMonth) {
		int m = date.getMonthValue();
		int d = date.getDayOfMonth();
		return switch (lang) {
			case KO -> withMonth ? m + "월 " + d + "일" : d + "일";
			case EN -> withMonth ? MONTHS_EN[m - 1] + " " + d : String.valueOf(d);
			case JA, ZH_HANS, ZH_HANT -> withMonth ? m + "月" + d + "日" : d + "日";
		};
	}

	private static final String[] MONTHS_EN = {
		"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
	};

	// ── 일정이 만들어졌다 ────────────────────────────────────────────────────

	public static String createdTitle(Lang lang) {
		return switch (lang) {
			case KO -> "부산 여행 일정이 완성됐어요";
			case EN -> "Your Busan trip plan is ready";
			case JA -> "釜山旅行の旅程ができました";
			case ZH_HANS, ZH_HANT -> "釜山旅行行程已完成";
		};
	}

	public static String createdBody(String tripLabel, Lang lang) {
		return tripLabel + " · " + switch (lang) {
			case KO -> "눌러서 일정 보기";
			case EN -> "Tap to see your plan";
			case JA -> "タップして旅程を見る";
			case ZH_HANS -> "点击查看行程";
			case ZH_HANT -> "點擊查看行程";
		};
	}

	// ── 동행이 일정을 바꿨다 ─────────────────────────────────────────────────

	/**
	 * 제목 — 누가 했는지 알면 「수민님이 장소를 뺐어요」, 모르면(탈퇴) 「장소가 빠졌어요」.
	 * 🔴 {@code default} 를 두지 않는다. 판 종류가 늘면 여기가 컴파일 오류로 막힌다.
	 */
	public static String editTitle(ItineraryVersion.Operation operation, String actorName, Lang lang) {
		boolean named = actorName != null && !actorName.isBlank();
		if (!named) {
			return passive(operation, lang);
		}
		String who = actorName.trim();
		return switch (lang) {
			case KO -> who + "님이 " + verbKo(operation);
			case EN -> who + " " + verbEn(operation);
			case JA -> who + "さんが" + verbJa(operation);
			case ZH_HANS -> who + verbZhHans(operation);
			case ZH_HANT -> who + verbZhHant(operation);
		};
	}

	public static String editBody(String tripLabel, Lang lang) {
		return tripLabel + " · " + switch (lang) {
			case KO -> "눌러서 바뀐 일정 보기";
			case EN -> "Tap to see what changed";
			case JA -> "タップして変更を見る";
			case ZH_HANS -> "点击查看更改";
			case ZH_HANT -> "點擊查看變更";
		};
	}

	private static String verbKo(ItineraryVersion.Operation operation) {
		return switch (operation) {
			case CREATE -> "일정을 만들었어요";
			case REGENERATE, REGENERATE_DAY, REPLAN_DAY -> "남은 일정을 다시 계획했어요";
			case REVERT -> "변경을 되돌렸어요";
			case REORDER -> "일정 순서를 바꿨어요";
			case LOCK_ITEM -> "장소를 고정했어요";
			case REMOVE_ITEM -> "장소를 뺐어요";
			case ADD_ITEM, REPLACE_ITEM -> "장소를 더했어요";
		};
	}

	private static String verbEn(ItineraryVersion.Operation operation) {
		return switch (operation) {
			case CREATE -> "made the plan";
			case REGENERATE, REGENERATE_DAY, REPLAN_DAY -> "replanned the rest of the trip";
			case REVERT -> "undid a change";
			case REORDER -> "reordered the plan";
			case LOCK_ITEM -> "locked a place";
			case REMOVE_ITEM -> "removed a place";
			case ADD_ITEM, REPLACE_ITEM -> "added a place";
		};
	}

	private static String verbJa(ItineraryVersion.Operation operation) {
		return switch (operation) {
			case CREATE -> "旅程を作りました";
			case REGENERATE, REGENERATE_DAY, REPLAN_DAY -> "残りの旅程を組み直しました";
			case REVERT -> "変更を元に戻しました";
			case REORDER -> "旅程の順番を変えました";
			case LOCK_ITEM -> "場所を固定しました";
			case REMOVE_ITEM -> "場所を外しました";
			case ADD_ITEM, REPLACE_ITEM -> "場所を追加しました";
		};
	}

	private static String verbZhHans(ItineraryVersion.Operation operation) {
		return switch (operation) {
			case CREATE -> "创建了行程";
			case REGENERATE, REGENERATE_DAY, REPLAN_DAY -> "重新规划了剩余行程";
			case REVERT -> "撤销了一处更改";
			case REORDER -> "调整了行程顺序";
			case LOCK_ITEM -> "锁定了一个地点";
			case REMOVE_ITEM -> "移除了一个地点";
			case ADD_ITEM, REPLACE_ITEM -> "添加了一个地点";
		};
	}

	private static String verbZhHant(ItineraryVersion.Operation operation) {
		return switch (operation) {
			case CREATE -> "建立了行程";
			case REGENERATE, REGENERATE_DAY, REPLAN_DAY -> "重新規劃了剩餘行程";
			case REVERT -> "撤銷了一處變更";
			case REORDER -> "調整了行程順序";
			case LOCK_ITEM -> "鎖定了一個地點";
			case REMOVE_ITEM -> "移除了一個地點";
			case ADD_ITEM, REPLACE_ITEM -> "新增了一個地點";
		};
	}

	/** 누가 했는지 모를 때(탈퇴) — 「누군가」를 지어내지 않고 일어난 일만. */
	private static String passive(ItineraryVersion.Operation operation, Lang lang) {
		return switch (lang) {
			case KO -> switch (operation) {
				case CREATE -> "일정이 만들어졌어요";
				case REGENERATE, REGENERATE_DAY, REPLAN_DAY -> "남은 일정을 다시 계획했어요";
				case REVERT -> "변경을 되돌렸어요";
				case REORDER -> "일정 순서가 바뀌었어요";
				case LOCK_ITEM -> "장소가 고정됐어요";
				case REMOVE_ITEM -> "장소가 빠졌어요";
				case ADD_ITEM, REPLACE_ITEM -> "장소가 더해졌어요";
			};
			case EN -> switch (operation) {
				case CREATE -> "A plan was made";
				case REGENERATE, REGENERATE_DAY, REPLAN_DAY -> "The rest of the trip was replanned";
				case REVERT -> "A change was undone";
				case REORDER -> "The plan order changed";
				case LOCK_ITEM -> "A place was locked";
				case REMOVE_ITEM -> "A place was removed";
				case ADD_ITEM, REPLACE_ITEM -> "A place was added";
			};
			case JA -> switch (operation) {
				case CREATE -> "旅程が作られました";
				case REGENERATE, REGENERATE_DAY, REPLAN_DAY -> "残りの旅程が組み直されました";
				case REVERT -> "変更が元に戻されました";
				case REORDER -> "旅程の順番が変わりました";
				case LOCK_ITEM -> "場所が固定されました";
				case REMOVE_ITEM -> "場所が外されました";
				case ADD_ITEM, REPLACE_ITEM -> "場所が追加されました";
			};
			case ZH_HANS -> switch (operation) {
				case CREATE -> "行程已创建";
				case REGENERATE, REGENERATE_DAY, REPLAN_DAY -> "剩余行程已重新规划";
				case REVERT -> "更改已撤销";
				case REORDER -> "行程顺序已调整";
				case LOCK_ITEM -> "地点已锁定";
				case REMOVE_ITEM -> "地点已移除";
				case ADD_ITEM, REPLACE_ITEM -> "地点已添加";
			};
			case ZH_HANT -> switch (operation) {
				case CREATE -> "行程已建立";
				case REGENERATE, REGENERATE_DAY, REPLAN_DAY -> "剩餘行程已重新規劃";
				case REVERT -> "變更已撤銷";
				case REORDER -> "行程順序已調整";
				case LOCK_ITEM -> "地點已鎖定";
				case REMOVE_ITEM -> "地點已移除";
				case ADD_ITEM, REPLACE_ITEM -> "地點已新增";
			};
		};
	}

	// ── 동행이 들어왔다 · 역할이 바뀌었다 ───────────────────────────────────────

	public static String joinedTitle(String name, Lang lang) {
		boolean named = name != null && !name.isBlank();
		String who = named ? name.trim() : null;
		return switch (lang) {
			case KO -> named ? who + "님이 여행에 합류했어요" : "동행이 합류했어요";
			case EN -> named ? who + " joined your trip" : "A companion joined your trip";
			case JA -> named ? who + "さんが旅行に参加しました" : "同行者が参加しました";
			case ZH_HANS, ZH_HANT -> named ? who + "加入了旅行" : "同行者加入了旅行";
		};
	}

	public static String roleTitle(boolean canEdit, Lang lang) {
		return switch (lang) {
			case KO -> canEdit ? "이제 일정을 함께 고칠 수 있어요" : "이제 일정을 보기만 할 수 있어요";
			case EN -> canEdit ? "You can now edit the plan together" : "You can now only view the plan";
			case JA -> canEdit ? "旅程を一緒に編集できるようになりました" : "旅程は閲覧のみになりました";
			case ZH_HANS -> canEdit ? "现在可以一起编辑行程了" : "现在只能查看行程";
			case ZH_HANT -> canEdit ? "現在可以一起編輯行程了" : "現在只能查看行程";
		};
	}

	/** 「10월 1일 – 3일 · 수민님이 바꿨어요」. 누가 했는지 모르면 여행 이름만. */
	public static String roleBody(String tripLabel, String actorName, Lang lang) {
		if (actorName == null || actorName.isBlank()) {
			return tripLabel;
		}
		String who = actorName.trim();
		return tripLabel + " · " + switch (lang) {
			case KO -> who + "님이 바꿨어요";
			case EN -> "changed by " + who;
			case JA -> who + "さんが変更しました";
			case ZH_HANS -> "由" + who + "更改";
			case ZH_HANT -> "由" + who + "變更";
		};
	}
}
