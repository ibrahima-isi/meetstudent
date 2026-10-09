import { loadRuntimeConfig } from './runtime-config';

const reply = (body: unknown, ok = true) =>
  (() => Promise.resolve({ ok, json: () => Promise.resolve(body) })) as unknown as typeof fetch;

describe('loadRuntimeConfig', () => {
  it('returns the apiUrl from config.json, without trailing slash', async () => {
    expect(await loadRuntimeConfig(reply({ apiUrl: 'https://api.x.io/api/v1/' }))).toEqual({
      apiUrl: 'https://api.x.io/api/v1',
    });
  });

  it('falls back to an empty config on HTTP error', async () => {
    expect(await loadRuntimeConfig(reply({}, false))).toEqual({});
  });

  it('falls back when the fetch rejects or the body is not JSON', async () => {
    expect(await loadRuntimeConfig(() => Promise.reject(new Error('net')))).toEqual({});
    const badJson = (() =>
      Promise.resolve({ ok: true, json: () => Promise.reject(new Error('x')) })) as unknown as typeof fetch;
    expect(await loadRuntimeConfig(badJson)).toEqual({});
  });

  it('ignores a missing or non-string apiUrl', async () => {
    expect(await loadRuntimeConfig(reply({ apiUrl: 42 }))).toEqual({});
    expect(await loadRuntimeConfig(reply({ apiUrl: '  ' }))).toEqual({});
  });
});
