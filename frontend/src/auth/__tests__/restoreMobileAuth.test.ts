import { ApiClientError } from '@/api/client';
import { restoreMobileAuth } from '../restoreMobileAuth';
import type { AuthTokens } from '../authApi';

const tokens: AuthTokens = { accessToken: 'access', refreshToken: 'rotated', expiresIn: 300, sessionId: 'session', user: { userId: 'id', email: 'test@example.invalid', displayName: 'Traveler', language: 'EN', status: 'ACTIVE' } };
const storage = () => ({ read: jest.fn().mockResolvedValue('stored'), write: jest.fn().mockResolvedValue(undefined), remove: jest.fn().mockResolvedValue(undefined) });
it('does not call the server when no session is stored', async () => {
  const store = storage(); store.read.mockResolvedValue(null);
  const refresh = jest.fn();
  expect(await restoreMobileAuth(store, refresh)).toBeNull();
  expect(refresh).not.toHaveBeenCalled();
});
it('saves the rotated token and uses the authenticated user from the refresh response', async () => {
  const store = storage();
  expect(await restoreMobileAuth(store, jest.fn().mockResolvedValue(tokens))).toBe(tokens);
  expect(store.write).toHaveBeenCalledWith('rotated');
  expect(store.remove).not.toHaveBeenCalled();
});
it.each([0, 429, 500, 503])('preserves credentials on transient failure %s', async (status) => {
  const store = storage();
  const error = new ApiClientError('temporary', 'TEMPORARY', status);
  await expect(restoreMobileAuth(store, jest.fn().mockRejectedValue(error))).rejects.toBe(error);
  expect(store.remove).not.toHaveBeenCalled();
});
it('deletes a session explicitly rejected by the server', async () => {
  const store = storage();
  await expect(restoreMobileAuth(store, jest.fn().mockRejectedValue(new ApiClientError('expired', 'EXPIRED', 401)))).rejects.toThrow('expired');
  expect(store.remove).toHaveBeenCalledTimes(1);
});
it('does not erase credentials when storing the rotated token fails', async () => {
  const store = storage(); store.write.mockRejectedValue(new Error('storage unavailable'));
  await expect(restoreMobileAuth(store, jest.fn().mockResolvedValue(tokens))).rejects.toThrow('storage unavailable');
  expect(store.remove).not.toHaveBeenCalled();
});
