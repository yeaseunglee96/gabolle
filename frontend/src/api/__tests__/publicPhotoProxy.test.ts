// S15P21E201-1955 — 안드로이드가 못 받는 www.visitbusan.net 사진을 서버 프록시 주소로 바꾼다.
// 사진 칸만 바꾸고, 같은 호스트의 출처 링크·다른 호스트는 그대로 둔다.
import { proxiedPhotoUrl, proxyPublicPhotos } from '../publicPhotoProxy';

const BASE = 'https://j15e201.p.ssafy.io';
const VB = 'https://www.visitbusan.net/uploadImgs/files/cntnts/20230612143356750_ttiel';
const PROXIED = `${BASE}/api/v1/images/proxy?url=${encodeURIComponent(VB)}`;

describe('proxiedPhotoUrl', () => {
  it('visitbusan 사진 주소를 프록시 주소로 바꾼다', () => {
    expect(proxiedPhotoUrl(VB, BASE)).toBe(PROXIED);
    expect(proxiedPhotoUrl('https://WWW.VISITBUSAN.NET/a.jpg', BASE)).toBe(
      `${BASE}/api/v1/images/proxy?url=${encodeURIComponent('https://WWW.VISITBUSAN.NET/a.jpg')}`);
  });

  it.each([
    'https://tong.visitkorea.or.kr/cms/resource/68/3026468_image2_1.JPG', // 사슬이 온전한 호스트
    'https://archive.visitbusan.net/file/1', // 같은 이름이 들어간 다른 호스트
    'https://www.visitbusan.net.evil.com/a.jpg',
    'https://www.visitbusan.net@evil.com/a.jpg',
    'http://www.visitbusan.net/a.jpg', // 서버는 https 만 받는다
    '/api/v1/uploads/images/story/a.jpg',
    '',
  ])('%s 는 그대로 둔다', (url) => {
    expect(proxiedPhotoUrl(url, BASE)).toBe(url);
  });
});

describe('proxyPublicPhotos', () => {
  it('여행 표지·장소 사진·사진 목록의 url 을 바꾼다', () => {
    const data = {
      items: [
        { tripId: 't1', coverImageUrl: VB },
        { tripId: 't2', coverImageUrl: 'https://tong.visitkorea.or.kr/x.jpg' },
      ],
      place: {
        photoUrl: VB,
        photos: [{ url: VB, source: '출처 : 부산관광아카이브', license: { name: '공공누리 제1유형', url: VB, filePage: VB } }],
      },
      thumbnails: [VB],
    };

    const out = proxyPublicPhotos(data, BASE);

    expect(out.items[0].coverImageUrl).toBe(PROXIED);
    expect(out.items[1].coverImageUrl).toBe('https://tong.visitkorea.or.kr/x.jpg');
    expect(out.place.photoUrl).toBe(PROXIED);
    expect(out.place.photos[0].url).toBe(PROXIED);
    expect(out.thumbnails[0]).toBe(PROXIED);
    // 출처는 그대로 — 출처 링크(웹 페이지)를 프록시로 바꾸면 링크가 깨진다.
    expect(out.place.photos[0].source).toBe('출처 : 부산관광아카이브');
    expect(out.place.photos[0].license.url).toBe(VB);
    expect(out.place.photos[0].license.filePage).toBe(VB);
  });

  it('사진 칸이 아닌 visitbusan 링크는 그대로 둔다', () => {
    const data = { festival: { homepage: VB, url: VB, title: '부산불꽃축제' } };
    expect(proxyPublicPhotos(data, BASE)).toBe(data);
  });

  it('바꿀 것이 없으면 같은 객체를 돌려준다 — 화면이 쓸데없이 다시 그려지지 않게', () => {
    const data = { items: [{ photoUrl: 'https://tong.visitkorea.or.kr/x.jpg' }], total: 1 };
    expect(proxyPublicPhotos(data, BASE)).toBe(data);
  });

  it('글자·숫자·null·undefined 도 그대로 통과한다', () => {
    expect(proxyPublicPhotos(null, BASE)).toBeNull();
    expect(proxyPublicPhotos(undefined, BASE)).toBeUndefined();
    expect(proxyPublicPhotos(3, BASE)).toBe(3);
    expect(proxyPublicPhotos('x', BASE)).toBe('x');
  });
});
