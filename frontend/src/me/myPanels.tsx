// 마이페이지에서 겹쳐 여는 패널들 — 어떤 것이 옮겨졌고 어떤 것이 아직인지 한 곳에서 정한다.
//
// 🔴 **하위 화면의 주소는 그대로 살아 있다.** 딥링크로 들어오거나 새로고침하면 여전히 그
//    화면이 열려야 한다. 여기 있는 것은 「마이페이지에서 눌렀을 때」의 길뿐이다.
//
// 🔴 아직 안 옮긴 메뉴는 `null` 이다. 부르는 쪽이 그때는 지금처럼 화면을 바꾼다 — 한 번에
//    열한 개를 다 옮기면 무엇이 깨졌는지 못 찾는다.
import type { ReactNode } from 'react';

import { BlockedAccountsBody } from '@/me/panels/BlockedAccountsBody';
import { HelpBody } from '@/me/panels/HelpBody';
import { IdentitiesBody } from '@/me/panels/IdentitiesBody';
import { MyPostsBody } from '@/me/panels/MyPostsBody';
import { NotificationsBody } from '@/me/panels/NotificationsBody';
import { PreferencesBody } from '@/me/panels/PreferencesBody';
import { ProfileBody } from '@/me/panels/ProfileBody';
import { RelationBody } from '@/me/panels/RelationBody';
import { SavedRecordsBody } from '@/me/panels/SavedRecordsBody';
import { TermsBody } from '@/me/panels/TermsBody';

export type MyPanelKey =
  | 'posts' | 'saved' | 'followers' | 'following' | 'preferences'
  | 'identities' | 'profile' | 'notifications' | 'blocked' | 'help' | 'terms';

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
    case 'followers': return { title: tx('팔로워', 'Followers'), description: tx('나를 팔로우하는 사람들이에요.', 'People who follow you.') };
    case 'following': return { title: tx('팔로잉', 'Following'), description: tx('내가 팔로우하는 사람들이에요.', 'People you follow.') };
    case 'preferences': return { title: tx('여행 취향', 'Travel preferences'), description: tx('여행을 만들 때 이 답이 미리 채워져요. 여기서 고치면 다음 여행부터 바뀌어요. 이미 만든 여행은 그대로예요.', 'These are filled in when you plan a trip. Changes here apply from your next trip. Trips you already made stay as they are.') };
    case 'identities': return { title: tx('연결된 소셜 계정', 'Connected accounts'), description: tx('로그인에 쓰는 계정이에요.', 'Accounts you can sign in with.') };
    case 'profile': return { title: tx('프로필', 'Profile'), description: tx('피드와 기록에 보이는 이름과 사진이에요.', 'This is the name and photo people see on your posts.') };
    case 'notifications': return { title: tx('알림', 'Notifications'), description: tx('나에게 온 소식이에요.', 'Updates for you.') };
    case 'blocked': return { title: tx('차단된 계정', 'Blocked accounts'), description: tx('차단한 사람에게는 내 글이 보이지 않아요.', "People you've blocked can't see your posts.") };
    case 'help': return { title: tx('도움말·문의', 'Help & support'), description: tx('앱 소개, 자주 묻는 질문, 문제 해결', 'App tour, FAQs, and troubleshooting') };
    case 'terms': return { title: tx('약관·고지', 'Terms & notices'), description: tx('가볼래를 쓰실 때 적용되는 약관과, 이 앱이 쓰는 자료의 출처예요.', 'The terms that apply to Gabolle, and where the data in this app comes from.') };
  }
}

/**
 * 그 패널의 본문. **아직 안 옮겼으면 `null`** 이고, 그때 부르는 쪽은 화면을 바꾼다.
 *
 * 옮기는 방식은 하나다 — 화면 파일에서 **본문만** 떼어 `panels/` 안으로 옮기고, 원래 화면과
 * 패널이 **같은 것**을 그린다. 두 벌이 되면 한쪽만 고쳐지고, 그 차이는 두 길로 들어가
 * 나란히 봐야만 보인다.
 */
export function myPanelBody(key: MyPanelKey): ReactNode | null {
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
    case 'preferences': return <PreferencesBody />;
    case 'profile': return <ProfileBody />;
  }
}

/** 겹쳐서 열 수 있는가. 아니면 부르는 쪽이 화면을 바꾼다. */
export function hasPanel(key: MyPanelKey): boolean {
  return myPanelBody(key) !== null;
}
