import { applyServerEnvironment, readAllowedHosts, RuntimeEnvironment } from './server-environment';

describe('applyServerEnvironment', () => {
  let env: RuntimeEnvironment;

  beforeEach(() => {
    env = {
      apiUrl: 'http://localhost:8080/api/v1',
      serverUrl: 'http://localhost:8080',
    };
  });

  it('overrides apiUrl from API_URL', () => {
    applyServerEnvironment(env, { API_URL: 'http://api:8080/api/v1' });

    expect(env.apiUrl).toBe('http://api:8080/api/v1');
  });

  it('overrides serverUrl from SERVER_URL', () => {
    applyServerEnvironment(env, { SERVER_URL: 'http://api:8080' });

    expect(env.serverUrl).toBe('http://api:8080');
  });

  it('keeps the build-time values when no variable is set', () => {
    applyServerEnvironment(env, {});

    expect(env.apiUrl).toBe('http://localhost:8080/api/v1');
    expect(env.serverUrl).toBe('http://localhost:8080');
  });

  it('ignores empty and whitespace-only values', () => {
    applyServerEnvironment(env, { API_URL: '', SERVER_URL: '   ' });

    expect(env.apiUrl).toBe('http://localhost:8080/api/v1');
    expect(env.serverUrl).toBe('http://localhost:8080');
  });

  it('strips trailing slashes so callers can safely append a path', () => {
    applyServerEnvironment(env, {
      API_URL: 'http://api:8080/api/v1/',
      SERVER_URL: 'http://api:8080//',
    });

    expect(env.apiUrl).toBe('http://api:8080/api/v1');
    expect(env.serverUrl).toBe('http://api:8080');
  });

  it('mutates the object in place so modules holding the reference see the change', () => {
    const held = env;

    applyServerEnvironment(env, { API_URL: 'http://api:8080/api/v1' });

    expect(held.apiUrl).toBe('http://api:8080/api/v1');
  });
});

describe('readAllowedHosts', () => {
  it('splits a comma-separated list and trims each entry', () => {
    expect(readAllowedHosts({ ALLOWED_HOSTS: 'localhost, meetstudent.app ,web' }))
      .toEqual(['localhost', 'meetstudent.app', 'web']);
  });

  it('defaults to localhost and the compose service name when unset', () => {
    expect(readAllowedHosts({})).toEqual(['localhost', 'web']);
  });

  it('treats an empty or blank value as unset', () => {
    expect(readAllowedHosts({ ALLOWED_HOSTS: '   ' })).toEqual(['localhost', 'web']);
  });

  it('drops empty entries rather than allowing an empty hostname', () => {
    expect(readAllowedHosts({ ALLOWED_HOSTS: 'a,,b,' })).toEqual(['a', 'b']);
  });

  it('falls back to the default when the value parses to no hosts', () => {
    expect(readAllowedHosts({ ALLOWED_HOSTS: ',' })).toEqual(['localhost', 'web']);
    expect(readAllowedHosts({ ALLOWED_HOSTS: ' , ' })).toEqual(['localhost', 'web']);
    expect(readAllowedHosts({ ALLOWED_HOSTS: ',,' })).toEqual(['localhost', 'web']);
  });
});
