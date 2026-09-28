// 마이페이지에서 겹쳐 여는 패널 열한 개.
//
// 🔴 -1331 정정 — 예전 주석은 *"하위 화면의 주소는 그대로 살아 있다"* 고 적고 있었는데
//    **이제 아니다.** `app/me/` 아래 일곱은 없앴다. 마이페이지 하나에서 겹쳐 여는 것이
//    유일한 길이고, 밖에서 지목할 때는 `/me?panel=<열쇠>` 로 온다.
//
// 🔴 옮기는 방식은 하나다 — 화면 파일에서 **본문만** 떼어 `panels/` 안으로 옮기고, 원래
//    화면과 패널이 **같은 것**을 그린다. 두 벌이 되면 한쪽만 고쳐지고, 그 차이는 두 길로
//    들어가 나란히 봐야만 보인다.
import type { ReactNode } from 'react';

import { BlockedAccountsBody } from '@/me/panels/BlockedAccountsBody';
import { HelpBody } from '@/me/panels/HelpBody';
import { IdentitiesBody } from '@/me/panels/IdentitiesBody';
import { MyPostsBody } from '@/me/panels/MyPostsBody';
import { MyRepliesBody } from '@/me/panels/MyRepliesBody';
import { NotificationsBody } from '@/me/panels/NotificationsBody';
import { PreferencesBody } from '@/me/panels/PreferencesBody';
import { ProfileBody } from '@/me/panels/ProfileBody';
import { RelationBody } from '@/me/panels/RelationBody';
import { SavedRecordsBody } from '@/me/panels/SavedRecordsBody';
import { TermsBody } from '@/me/panels/TermsBody';

export type MyPanelKey =
  | 'posts' | 'saved' | 'replies' | 'followers' | 'following' | 'preferences'
  | 'identities' | 'profile' | 'delete-account' | 'notifications' | 'blocked' | 'help' | 'terms';

type Translate = (ko: string, en: string) => string;

/**
 * 패널 머리에 들어가는 글 — 제목과 한 줄 설명.
 *
 * 🔴 지금 화면이 쓰는 문구를 **그대로** 쓴다. 여기서 새로 쓰면 같은 곳을 두 이름으로 부르게 되고,
 *    짧게 줄이면 원래 화면이 하던 설명이 조용히 사라진다 — 실제로 넷을 줄여 놨다가 되돌렸다.
 */
export function panelTitle(key: MyPanelKey, tx: Translate): { title: string; description: string } {
  switch (key) {
    case 'posts': return { title: tx('내 기록', 'My records'), description: tx('피드에 남긴 내 글이에요. 공개 범위는 쓸 때 정한 그대로 보여드려요.', 'These are the posts you left on the feed, with the visibility you chose when writing them.') };
    case 'saved': return { title: tx('저장한 기록', 'Saved records'), description: tx('다른 여행자의 기록 중 눌러 담아 둔 것이에요.', 'Records from other travellers that you bookmarked.') };
    case 'replies': return { title: tx('내 댓글', 'My comments'), description: tx('내가 남긴 댓글이에요. 원글이 지워지거나 가려져도 여기서 찾고 지울 수 있어요.', 'Comments you left. You can find and delete them here even if the original post was deleted or hidden.') };
    case 'followers': return { title: tx('팔로워', 'Followers'), description: tx('나를 팔로우하는 사람들이에요.', 'People who follow you.') };
    case 'following': return { title: tx('팔로잉', 'Following'), description: tx('내가 팔로우하는 사람들이에요.', 'People you follow.') };
    case 'preferences': return { title: tx('여행 취향', 'Travel preferences'), description: tx('여행을 만들 때 이 답이 미리 채워져요. 여기서 고치면 다음 여행부터 바뀌어요. 이미 만든 여행은 그대로예요.', 'These are filled in when you plan a trip. Changes here apply from your next trip. Trips you already made stay as they are.') };
    case 'identities': return { title: tx('연결된 소셜 계정', 'Connected accounts'), description: tx('로그인에 쓰는 계정이에요.', 'Accounts you can sign in with.') };
    case 'profile': return { title: tx('프로필', 'Profile'), description: tx('피드와 기록에 보이는 이름과 사진이에요.', 'This is the name and photo people see on your posts.') };
    // 설정에서 바로 들어오는 문 — 탈퇴 흐름은 프로필 패널 안에 있고, 여기서는 그 흐름을 열어 둔 채로 연다(S15P21E201-1401).
    case 'delete-account': return { title: tx('회원 탈퇴', 'Delete account'), description: tx('여행, 기록, 취향이 모두 지워지고 되돌릴 수 없어요.', 'Your trips, records, and preferences are all deleted. This cannot be undone.') };
    case 'notifications': return { title: tx('알림', 'Notifications'), description: tx('나에게 온 소식이에요.', 'Updates for you.') };
    // 🔴 S15P21E201-1722 — S15P21E201-1714 로 차단이 양방향이 됐는데 이 설명은 한 방향만 말하고 있었다.
    case 'blocked': return { title: tx('차단된 계정', 'Blocked accounts'), description: tx('차단한 사람에게는 내 글이 안 보이고, 내 피드에도 그 사람 글이 안 보여요.', "People you've blocked can't see your posts, and their posts won't show up in your feed either.") };
    case 'help': return { title: tx('도움말·문의', 'Help & support'), description: tx('앱 소개, 자주 묻는 질문, 문제 해결', 'App tour, FAQs, and troubleshooting') };
    case 'terms': return { title: tx('약관·고지', 'Terms & notices'), description: tx('가볼래를 쓰실 때 적용되는 약관과, 이 앱이 쓰는 자료의 출처예요.', 'The terms that apply to Gabolle, and where the data in this app comes from.') };
  }
}

/**
 * 그 패널의 본문.
 *
 * 🔴 **`null` 을 돌려주지 않는다.** 예전에는 「아직 안 옮긴 것」이 `null` 이었고 부르는 쪽이
 * 그때 화면을 바꿨다. 이제 열한 개가 다 옮겨져서 그 길이 없다 — 반환형에서 `null` 을
 * 빼 두면, 열쇠를 하나 더 늘리고 본문을 안 적었을 때 **타입이 그 자리를 잡는다.**
 */
export function myPanelBody(key: MyPanelKey): ReactNode {
  // 🔴 default 를 두지 않는다. 키가 하나 늘면 타입이 「여기 빠졌다」고 잡는다 —
  //    default 가 있으면 조용히 null 이 되고, 그 메뉴는 빈 패널이 열린다.
  switch (key) {
    case 'blocked': return <BlockedAccountsBody />;
    case 'terms': return <TermsBody />;
    case 'followers': return <RelationBody kind="followers" />;
    case 'following': return <RelationBody kind="following" />;
    case 'help': return <HelpBody />;
    case 'identities': return <IdentitiesBody />;
    case 'notifications': return <NotificationsBody />;
    case 'posts': return <MyPostsBody />;
    case 'saved': return <SavedRecordsBody />;
    case 'replies': return <MyRepliesBody />;
    case 'preferences': return <PreferencesBody />;
    case 'profile': return <ProfileBody />;
    case 'delete-account': return <ProfileBody startDeletion />;
  }
}

/** 밖에서 온 `/me?panel=…` 값이 우리가 아는 열쇠인가 — 모르는 값이면 아무것도 안 연다. */
export function isPanelKey(value: unknown): value is MyPanelKey {
  return typeof value === 'string' && PANEL_KEYS.includes(value as MyPanelKey);
}

const PANEL_KEYS: readonly MyPanelKey[] = [
  'posts', 'saved', 'replies', 'followers', 'following', 'preferences',
  'identities', 'profile', 'delete-account', 'notifications', 'blocked', 'help', 'terms',
];
