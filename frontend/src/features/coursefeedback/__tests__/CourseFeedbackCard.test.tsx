// 코스별 품질 피드백 카드 — S15P21E201-2020.
//
// 🔴 이 시험이 지키는 것:
//    1. 아직 안 남겼으면 👍/👎 두 버튼, 남겼으면 내 평가와 「바꾸기」가 보인다(다시 열면 내 평가가 보인다 — 완료 기준)
//    2. 이유는 고른 쪽(좋아요/아쉬워요)에 맞는 것만 보이고, 쪽을 바꾸면 안 맞는 이유는 빠진다
//    3. 저장하면 고른 값 그대로 서버에 가고, 카드가 저장된 평가로 바뀐다
//    4. 불러오기에 실패하면 카드를 안 그린다 — 「아직 안 남김」으로 보이면 또 누르게 된다
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react-native';

const mockLoad = jest.fn();
const mockSave = jest.fn();
jest.mock('../courseFeedbackApi', () => ({
  ...jest.requireActual('../courseFeedbackApi'),
  loadMyCourseFeedback: (...args: unknown[]) => mockLoad(...args),
  saveCourseFeedback: (...args: unknown[]) => mockSave(...args),
}));

import { CourseFeedbackCard } from '../CourseFeedbackCard';

const tx = (ko: string) => ko;
const saved = (rating: 'UP' | 'DOWN', reasons: string[]) => ({ itineraryId: 'it-1', version: 1, courseLabel: 'B', rating, reasons, comment: null, updatedAt: '2026-10-12T00:00:00Z' });

beforeEach(() => { mockLoad.mockReset(); mockSave.mockReset(); });

it('아직 안 남겼으면 두 버튼, 저장하면 고른 값이 서버로 가고 카드가 내 평가로 바뀐다', async () => {
  mockLoad.mockResolvedValue(null);
  mockSave.mockResolvedValue(saved('DOWN', ['TOO_TIGHT']));
  render(<CourseFeedbackCard itineraryId="it-1" courseLabel="B" accessToken="t" tx={tx} />);

  fireEvent.press(await screen.findByTestId('course-feedback-down'));
  expect(screen.getByTestId('course-feedback-reason-TOO_TIGHT')).toBeTruthy();
  expect(screen.queryByTestId('course-feedback-reason-GREAT_PLACES')).toBeNull();

  fireEvent.press(screen.getByTestId('course-feedback-reason-TOO_TIGHT'));
  fireEvent.changeText(screen.getByTestId('course-feedback-comment'), '  오후가 빠듯했어요 ');
  await act(async () => { fireEvent.press(screen.getByTestId('course-feedback-save')); });

  expect(mockSave).toHaveBeenCalledWith(expect.objectContaining({ itineraryId: 'it-1', rating: 'DOWN', reasons: ['TOO_TIGHT'], comment: '  오후가 빠듯했어요 ', courseLabel: 'B' }));
  await waitFor(() => expect(screen.getByText('👎 아쉬워요라고 남겼어요')).toBeTruthy());
  expect(screen.getByText('시간이 빠듯해요')).toBeTruthy();
  expect(screen.queryByTestId('course-feedback-sheet')).toBeNull();
});

it('쪽을 바꾸면 그쪽에 없는 이유는 빠진다', async () => {
  mockLoad.mockResolvedValue(saved('DOWN', ['LONG_TRANSIT']));
  mockSave.mockResolvedValue(saved('UP', []));
  render(<CourseFeedbackCard itineraryId="it-1" courseLabel="B" accessToken="t" tx={tx} />);

  fireEvent.press(await screen.findByTestId('course-feedback-edit'));
  fireEvent.press(screen.getByTestId('course-feedback-rating-UP'));
  await act(async () => { fireEvent.press(screen.getByTestId('course-feedback-save')); });
  expect(mockSave).toHaveBeenCalledWith(expect.objectContaining({ rating: 'UP', reasons: [] }));
});

it('끝난 여행은 「다녀온 코스」로 묻고, 불러오기에 실패하면 카드를 안 그린다', async () => {
  mockLoad.mockResolvedValueOnce(null);
  const { unmount } = render(<CourseFeedbackCard itineraryId="it-1" courseLabel="A" past accessToken="t" tx={tx} />);
  expect(await screen.findByText('다녀온 코스는 어땠어요?')).toBeTruthy();
  unmount();

  mockLoad.mockRejectedValueOnce(new Error('offline'));
  render(<CourseFeedbackCard itineraryId="it-2" courseLabel="A" accessToken="t" tx={tx} />);
  await act(async () => {});
  expect(screen.queryByTestId('course-feedback-card')).toBeNull();
});
