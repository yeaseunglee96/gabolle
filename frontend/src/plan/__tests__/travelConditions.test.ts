import AsyncStorage from '@react-native-async-storage/async-storage';

import {
  EMPTY_CONDITIONS,
  askConditionsAgain,
  conditionsFromDraft,
  conditionsToDraftPatch,
  consumeAskAgain,
  decodeConditions,
  encodeConditions,
  loadTravelConditions,
  saveTravelConditions,
  type TravelConditions,
} from '@/plan/travelConditions';
import { EMPTY_PLAN } from '@/plan/PlanProvider';

jest.mock('@/api/client', () => ({ apiRequest: jest.fn() }));
// eslint-disable-next-line @typescript-eslint/no-var-requires
const { apiRequest } = require('@/api/client') as { apiRequest: jest.Mock };

const filled: TravelConditions = {
  allergyStatus: 'VALUES', allergies: ['PEANUT', 'EGG'],
  dietStatus: 'NONE', dietTypes: [],
  maxWalkingDistanceM: 1000, slopeConstraint: 'AVOID', stairsConstraint: 'ALLOW', shadePreference: 'PREFER',
};

beforeEach(async () => { apiRequest.mockReset(); await AsyncStorage.clear(); });

describe('글자 한 덩어리로 담고 되읽기', () => {
  it('넣은 그대로 돌아온다', () => {
    expect(decodeConditions(encodeConditions(filled))).toEqual(filled);
  });

  it('🔴 「제한 없음」(0)과 「아직 안 정함」(null)은 다른 값이다', () => {
    expect(decodeConditions(encodeConditions({ ...filled, maxWalkingDistanceM: 0 }))?.maxWalkingDistanceM).toBe(0);
    expect(decodeConditions(encodeConditions({ ...filled, maxWalkingDistanceM: null }))?.maxWalkingDistanceM).toBeNull();
  });

  it('🔴 못 알아보는 글자는 반쯤 읽지 않고 통째로 버린다 — 알레르기는 안전에 걸리는 자리다', () => {
    expect(decodeConditions('{')).toBeNull();
    expect(decodeConditions(null)).toBeNull();
    expect(decodeConditions(JSON.stringify({ v: 999, allergyStatus: 'NONE', dietStatus: 'NONE' }))).toBeNull();
    expect(decodeConditions(JSON.stringify({ v: 1, allergyStatus: '아무거나', dietStatus: 'NONE' }))).toBeNull();
  });

  it('모르는 값이 섞여 오면 그 칸만 비운다', () => {
    const decoded = decodeConditions(JSON.stringify({ v: 1, allergyStatus: 'NONE', dietStatus: 'NONE', slopeConstraint: 'MAYBE' }));
    expect(decoded?.slopeConstraint).toBeNull();
  });

  it('🔴 「해당 없음」이면 고른 항목을 안 싣는다 — 둘이 같이 남으면 어느 쪽이 참인지 모른다', () => {
    const decoded = decodeConditions(JSON.stringify({ v: 1, allergyStatus: 'NONE', allergies: ['PEANUT'], dietStatus: 'NONE' }));
    expect(decoded?.allergies).toEqual([]);
  });
});

describe('초안과 주고받기', () => {
  it('초안에서 조건만 떼어낸다', () => {
    const draft = { ...EMPTY_PLAN, allergyStatus: 'VALUES' as const, allergies: ['EGG'], dietStatus: 'NONE' as const, dietTypes: ['VEGAN'] };
    expect(conditionsFromDraft(draft)).toMatchObject({ allergies: ['EGG'], dietTypes: [] });
  });

  it('🔴 초안에 얹을 때 「답했음」 표시를 같이 켠다 — 안 켜면 모달의 저장 단추가 다시 잠긴다', () => {
    const patch = conditionsToDraftPatch(filled);
    expect(patch.allergyAnswered).toBe(true);
    expect(patch.dietAnswered).toBe(true);
  });

  it('아무것도 안 정한 조건은 「답했음」을 안 켠다', () => {
    const patch = conditionsToDraftPatch(EMPTY_CONDITIONS);
    expect(patch.allergyAnswered).toBe(false);
    expect(patch.dietAnswered).toBe(false);
  });
});

describe('저장', () => {
  it('🔴 서버에 못 닿아도 기기에는 남는다 — 「저장했다」고 말해 놓고 잃지 않는다', async () => {
    apiRequest.mockRejectedValue(new Error('offline'));
    const result = await saveTravelConditions({ userId: 'u1', accessToken: 't', status: 'SAVED', conditions: filled });
    expect(result.synced).toBe(false);
    apiRequest.mockRejectedValue(new Error('offline'));
    expect((await loadTravelConditions('u1', 't')).conditions).toEqual(filled);
  });

  it('서버로 보낼 때 칸 이름은 answerStatus 다 — 응답의 status 와 다르다', async () => {
    apiRequest.mockResolvedValue({ status: 'SAVED', value: encodeConditions(filled) });
    await saveTravelConditions({ userId: 'u1', accessToken: 't', status: 'SAVED', conditions: filled });
    const [path, options] = apiRequest.mock.calls[0];
    expect(path).toBe('/api/v1/me/preferences/constraints');
    expect(options.method).toBe('PUT');
    expect(options.body.answerStatus).toBe('SAVED');
    expect(decodeConditions(options.body.value)).toEqual(filled);
  });

  it('🔴 SAVED 가 아니면 값을 안 싣는다 — 서버가 그때 값을 버린다', async () => {
    apiRequest.mockResolvedValue({ status: 'LATER', value: null });
    await saveTravelConditions({ userId: 'u1', accessToken: 't', status: 'LATER', conditions: filled });
    expect(apiRequest.mock.calls[0][1].body.value).toBeNull();
  });

  it('로그인 안 했으면 서버에 안 보낸다 — 기기에만 적는다', async () => {
    const result = await saveTravelConditions({ userId: null, accessToken: null, status: 'SAVED', conditions: filled });
    expect(apiRequest).not.toHaveBeenCalled();
    expect(result.synced).toBe(false);
    expect((await loadTravelConditions(null, null)).conditions).toEqual(filled);
  });
});

describe('읽기', () => {
  it('서버가 정본이다', async () => {
    await saveTravelConditions({ userId: 'u1', accessToken: null, status: 'SAVED', conditions: filled });
    apiRequest.mockResolvedValue({ status: 'SAVED', value: encodeConditions({ ...filled, allergies: ['MILK_DAIRY'] }) });
    expect((await loadTravelConditions('u1', 't')).conditions?.allergies).toEqual(['MILK_DAIRY']);
  });

  it('🔴 서버가 「한 번도 저장 안 함」을 주면 그대로 믿는다 — 다른 기기에서 지운 것이다', async () => {
    await saveTravelConditions({ userId: 'u1', accessToken: null, status: 'SAVED', conditions: filled });
    apiRequest.mockResolvedValue({ status: null, value: null });
    expect(await loadTravelConditions('u1', 't')).toEqual({ status: null, conditions: null });
  });

  it('🔴 서버에 못 닿으면 기기 사본으로 답한다 — 「안 물어봤다」고 단정하지 않는다', async () => {
    await saveTravelConditions({ userId: 'u1', accessToken: null, status: 'SAVED', conditions: filled });
    apiRequest.mockRejectedValue(new Error('offline'));
    const record = await loadTravelConditions('u1', 't');
    expect(record.status).toBe('SAVED');
    expect(record.conditions).toEqual(filled);
  });
});

describe('다시 묻기', () => {
  it('🔴 적어 둔 답을 지우지 않는다 — 다시 묻게 하려다 답을 잃지 않는다', async () => {
    await saveTravelConditions({ userId: 'u1', accessToken: null, status: 'SAVED', conditions: filled });
    await askConditionsAgain('u1');
    expect((await loadTravelConditions('u1', null)).conditions).toEqual(filled);
  });

  it('한 번 뜨고 나면 스스로 꺼진다', async () => {
    await askConditionsAgain('u1');
    expect(await consumeAskAgain('u1')).toBe(true);
    expect(await consumeAskAgain('u1')).toBe(false);
  });
});
