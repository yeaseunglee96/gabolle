// 장소 하트 — 데스크톱 홈과 폰 홈이 같이 쓴다.
//
// 원래 폰 홈 안에만 있었다. 시안 design_handoff_home_airbnb_rows 로 데스크톱에도 같은
// 하트가 생기면서 두 벌이 될 자리라 여기로 뺐다. 두 벌이 되면 한쪽만 고쳐지고, 그 차이는
// 두 화면을 나란히 눌러 봐야만 보인다.
import { useCallback, useEffect, useState } from 'react';

import { sendAppEvent } from '@/analytics/appEvents';
import { loadSavedPlaceIds, setSavedPlace } from '@/discovery/savedPlaces';
import { useI18n } from '@/i18n';

export type SavedPlaces = {
  likedIds: Set<string>;
  /** 방금 무슨 일이 일어났는지 한 줄. 아무 일도 없었으면 빈 문자열. */
  feedback: string;
  toggle: (placeId: string) => void;
};

/**
 * @param surface 분석에 남길 화면 이름. 하트가 어느 화면에서 눌렸는지 가른다.
 */
export function useSavedPlaces(accessToken: string | null, surface: string): SavedPlaces {
  const { tx } = useI18n();
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set());
  const [feedback, setFeedback] = useState('');

  // 계정 것과 기기 것을 합쳐서 본다(로그인 안 했으면 기기 것만).
  useEffect(() => {
    void loadSavedPlaceIds(accessToken).then((ids) => setLikedIds(new Set(ids)));
  }, [accessToken]);

  const toggle = useCallback((placeId: string) => {
    setLikedIds((current) => {
      const saved = !current.has(placeId);
      const next = new Set(current);
      if (saved) next.add(placeId); else next.delete(placeId);

      setFeedback(saved
        ? tx('장소를 저장했어요.', 'Saved this place.')
        : tx('저장을 해제했어요.', 'Unsaved this place on this device.'));

      // 하트는 즉시 반응해야 하므로 먼저 낙관적으로 그리고, 결과가 오면 문구만 바꾼다.
      // 서버가 못 받았으면 뒤늦게라도 사실대로 고쳐 말한다 — 저장된 척하지 않는다.
      void setSavedPlace(placeId, saved, accessToken).then(({ sync }) => {
        if (sync === 'failed') {
          setFeedback(tx(
            '이 기기에만 저장했어요. 서버에 아직 반영하지 못했어요.',
            'Saved on this device only — not synced to the server yet.',
          ));
        }
      });
      if (saved) sendAppEvent({ type: 'place_like', accessToken, payload: { place_id: placeId, surface } });

      return next;
    });
  }, [accessToken, surface, tx]);

  return { likedIds, feedback, toggle };
}
