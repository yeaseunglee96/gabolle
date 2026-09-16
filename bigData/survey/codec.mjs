/**
 * 완료 코드 — 인코딩·디코딩·검사합. 이 파일이 README.md 3절의 사양을 그대로 코드로
 * 옮긴 것이다. **사양이 바뀌면 이 파일과 README.md 를 같이 고친다.**
 *
 * 🔴 왜 Crockford Base32 인가. 이 알파벳은 원래부터 I·L·O·U 를 뺀다
 *    (I/L 은 1 과, O 는 0 과, U 는 V 와 손으로 옮겨 적을 때 헷갈린다). 새로 규칙을
 *    만들 필요가 없어서 그대로 가져다 쓴다.
 */
export const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'

/** 코드에 쓸 수 있는 글자인가. 대문자만 받는다 — 옮겨 적을 때 대소문자를 안 가린다. */
function indexOf(ch) {
  const i = ALPHABET.indexOf(ch)
  if (i < 0) throw new RangeError(`알파벳에 없는 글자: ${ch}`)
  return i
}

/**
 * 검사합 — payload(세션ID + 응답열) 를 2글자로 접는다.
 *
 * 자리마다 다른 가중치를 곱해서 더한다 — 두 글자를 맞바꿔 적는 실수(흔한 오기)도
 * 잡아내려는 것이다. 자리 가중치가 같으면 "AB" 와 "BA" 가 같은 검사합이 나온다.
 */
function checksumOf(payload) {
  let acc = 0
  for (let i = 0; i < payload.length; i++) {
    acc = (acc * 31 + (indexOf(payload[i]) + 1) * (i + 1)) % 1024
  }
  const hi = Math.floor(acc / 32)
  const lo = acc % 32
  return ALPHABET[hi] + ALPHABET[lo]
}

/** 무작위 세션 ID 4글자. 이름·연락처 등 실제 신원과 무관하다. */
export function randomSessionId(rng = Math.random) {
  let s = ''
  for (let i = 0; i < 4; i++) s += ALPHABET[Math.floor(rng() * ALPHABET.length)]
  return s
}

/**
 * 완료 코드를 만든다.
 * @param {string} sessionId 4글자, ALPHABET 안의 글자만
 * @param {('A'|'B')[]} choices 응답 순서 — data/staged/choice-design.json 의 sets 순서와 같아야 한다
 * @returns {string} `<세션ID>-<응답열>-<검사합>` 형태
 */
export function encodeSession(sessionId, choices) {
  if (sessionId.length !== 4) throw new RangeError('sessionId 는 4글자여야 한다')
  const answers = choices.join('')
  if (!/^[AB]+$/.test(answers)) throw new RangeError('choices 는 A 또는 B 만 담을 수 있다')
  const payload = sessionId + answers
  return `${sessionId}-${answers}-${checksumOf(payload)}`
}

/**
 * 완료 코드를 해독한다. 검사합이 안 맞으면 던지지 않고 `{ok:false}` 를 돌려준다 —
 * 호출부가 "걸린 코드를 목록으로 남긴다" 를 하기 편하게.
 * @param {string} code
 * @returns {{ok:true,sessionId:string,answers:string[]}|{ok:false,reason:string}}
 */
export function decodeCode(code) {
  const trimmed = code.trim().toUpperCase()
  const parts = trimmed.split('-')
  if (parts.length !== 3) return { ok: false, reason: `모양이 틀렸다(칸 ${parts.length}개, 3개여야 함): ${trimmed}` }
  const [sessionId, answers, given] = parts
  if (sessionId.length !== 4) return { ok: false, reason: `세션 ID 길이가 4가 아니다: ${sessionId}` }
  if (!answers.length || !/^[AB]+$/.test(answers)) return { ok: false, reason: `응답열이 A/B 만으로 이루어져 있지 않다: ${answers}` }
  if (given.length !== 2) return { ok: false, reason: `검사합 길이가 2가 아니다: ${given}` }

  let expected
  try {
    expected = checksumOf(sessionId + answers)
  } catch (e) {
    return { ok: false, reason: `알파벳에 없는 글자가 섞여 있다: ${e.message}` }
  }
  if (expected !== given) return { ok: false, reason: `검사합 불일치 (기대 ${expected}, 받음 ${given})` }

  return { ok: true, sessionId, answers: answers.split('') }
}
