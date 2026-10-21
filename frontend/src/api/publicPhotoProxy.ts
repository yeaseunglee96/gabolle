// 공공 사진을 우리 서버 프록시로 부른다 (S15P21E201-1955, 서버 S15P21E201-1954).
//
// 🔴 www.visitbusan.net 은 TLS 인증서 사슬의 가운데(중간 인증서)를 안 보낸다. 브라우저는 빠진 것을
//    스스로 받아 오지만 안드로이드는 받아 오지 않고 사진을 거절한다 — 그래서 내 여행·장소 사진이 회색
//    칸이었다. 서버의 `GET /api/v1/images/proxy?url=…` 가 그 사진을 대신 받아(저장해 두고) 넘겨 준다.
//
// 바꾸는 자리는 여기 한 곳이다 — apiRequest 가 받은 모든 응답이 이 함수를 지난다. 화면마다 고치면
// 새 화면이 생길 때마다 빠진다. 서버 주소는 client.ts 가 넘긴다(서로 불러오면 순환이 된다).
//
// 사진 칸만 바꾼다. 같은 호스트의 「출처」 링크(웹 페이지)까지 바꾸면 그 링크가 깨진다 — 그래서
// 칸 이름이 사진을 뜻할 때만(…photo…/…image…/…cover…/…thumb…, 또는 사진 목록 안의 url) 바꾼다.

/** 서버가 대신 받아 주는 호스트. 서버의 `gabolle.image-proxy.allowed-hosts` 와 같아야 한다. */
export const PROXIED_PHOTO_HOSTS = ['www.visitbusan.net'] as const;

const PROXY_PATH = '/api/v1/images/proxy';

/** 사진 칸 이름 — photoUrl · coverImageUrl · thumbnailUrl · imageUrl · heroImage … */
const PHOTO_KEY = /(photo|image|img|cover|thumb)/i;

/** 이 배열 안의 객체에서는 `url` 칸이 사진이다 — photos: [{ url, source, license }] */
const PHOTO_LIST_KEY = /(photos|images|gallery)$/i;

function hostOf(url: string): string | null {
  const m = /^https:\/\/([^/?#:@]+)(?::443)?(?:[/?#]|$)/i.exec(url);
  return m ? m[1].toLowerCase() : null;
}

/** 프록시가 받아 줄 주소면 프록시 주소로, 아니면 그대로. */
export function proxiedPhotoUrl(url: string, apiBase: string): string {
  if (typeof url !== 'string') return url;
  const host = hostOf(url.trim());
  if (!host || !(PROXIED_PHOTO_HOSTS as readonly string[]).includes(host)) return url;
  return `${apiBase}${PROXY_PATH}?url=${encodeURIComponent(url.trim())}`;
}

function rewrite(value: unknown, key: string, inPhotoList: boolean, apiBase: string): unknown {
  if (typeof value === 'string') {
    if (PHOTO_KEY.test(key) || (inPhotoList && key === 'url')) return proxiedPhotoUrl(value, apiBase);
    return value;
  }
  if (Array.isArray(value)) {
    const list = PHOTO_LIST_KEY.test(key);
    let changed = false;
    const out = value.map((item) => {
      // 사진 배열이 주소 글자만 담고 있을 때(photos: ["https://…"])도 바꾼다.
      const next = typeof item === 'string'
        ? (list || PHOTO_KEY.test(key) ? proxiedPhotoUrl(item, apiBase) : item)
        : rewrite(item, key, list, apiBase);
      if (next !== item) changed = true;
      return next;
    });
    return changed ? out : value;
  }
  if (value && typeof value === 'object') {
    let changed = false;
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      // 사진 목록의 칸 중 글자로 된 것만 「사진 목록 안의 url」로 본다 — 그 안의 license.url 같은 것은 아니다.
      const next = rewrite(v, k, inPhotoList && typeof v === 'string', apiBase);
      if (next !== v) changed = true;
      out[k] = next;
    }
    return changed ? out : value;
  }
  return value;
}

/** 응답 전체에서 프록시할 사진 주소만 바꾼다. 바꿀 것이 없으면 같은 객체를 그대로 돌려준다. */
export function proxyPublicPhotos<T>(data: T, apiBase: string): T {
  return rewrite(data, '', false, apiBase) as T;
}
