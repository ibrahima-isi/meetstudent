import { transferCacheOriginMap } from './http-transfer-origin';

describe('transferCacheOriginMap', () => {
  const request = (url: string) => new Request(url);

  it('maps the in-network API origin to the origin the visitor reached', () => {
    expect(
      transferCacheOriginMap('http://api:8080/api/v1', request('https://meetstudent.example/fr/home')),
    ).toEqual({ 'http://api:8080': 'https://meetstudent.example' });
  });

  it('maps nothing when the API URL is relative: there is no internal origin to hide', () => {
    expect(transferCacheOriginMap('/api/v1', request('https://meetstudent.example/fr'))).toEqual({});
  });

  it('maps nothing without a request (prerender, client-side render)', () => {
    expect(transferCacheOriginMap('http://api:8080/api/v1', null)).toEqual({});
  });
});
