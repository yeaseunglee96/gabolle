import { buildProtectedReturnTo } from '../ProtectedRoute';

describe('buildProtectedReturnTo', () => {
  it('keeps the trip preparation intent across sign-in', () => {
    expect(buildProtectedReturnTo('/trips', 'prepare')).toBe('/trips?open=prepare');
  });

  it('does not carry unrelated query values into authentication', () => {
    expect(buildProtectedReturnTo('/trips', 'unexpected')).toBe('/trips');
    expect(buildProtectedReturnTo('/me', 'prepare')).toBe('/me');
  });
});
