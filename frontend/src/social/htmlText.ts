// 서버가 HTML 인코딩해 보낸 글자를 되돌린다 — S15P21E201-1657(처음 만든 곳은 「내 댓글」 S15P21E201-1652).
//
// 운영 서버는 자유 입력(기록 작성자 이름·기록 본문·장소 후기 본문·내 댓글 미리보기)을 응답에 실을 때 HTML 인코딩한다
// (HtmlOutputEncoder — & → &amp; · < → &lt; · ' → &#39; …). 앱은 HTML 을 그리지 않고 글자를 그대로 찍으므로 받는 곳에서 되돌린다.
// 🔴 한 벌만 둔다 — 되돌리는 곳마다 따로 만들면 한쪽만 고쳐진다.

const NAMED: Record<string, string> = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };

/** 되돌린다 — 이름 붙은 다섯(&amp; &lt; &gt; &quot; &apos;)과 번호 표기(&#39; · &#x27;). 모르는 것은 그대로 둔다. */
export function decodeHtmlText(value: string): string {
  return value.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (whole, code: string) => {
    if (code[0] === '#') {
      const point = code[1] === 'x' || code[1] === 'X' ? parseInt(code.slice(2), 16) : parseInt(code.slice(1), 10);
      return Number.isFinite(point) && point > 0 && point <= 0x10ffff ? String.fromCodePoint(point) : whole;
    }
    return NAMED[code.toLowerCase()] ?? whole;
  });
}
