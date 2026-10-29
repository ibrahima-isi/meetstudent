import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ExpertStatus, User } from '@models/entities';
import { TokenService } from '@services/token.service';
import { ForumWriteState, injectForumWriteState } from './forum-access';

describe('injectForumWriteState', () => {
  let http: HttpTestingController;
  let capabilityCalls: number;
  let userReloads: number;
  /** What the server says the user is now, when the page asks again; `null` makes that call fail. Default: unchanged. */
  let fresh: User | null | undefined;

  function user(role: string, extra: Partial<User> = {}): User {
    return { id: 1, firstname: 'A', lastname: 'B', email: 'a@b.c', role: { id: 1, name: role }, ...extra } as User;
  }

  /** Signs `account` in (or nobody), and answers the capabilities call with `emailEnabled` if one is made. */
  async function stateFor(account: User | null, emailEnabled = true): Promise<ForumWriteState> {
    localStorage.clear();
    fresh = undefined;
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const tokens = TestBed.inject(TokenService);
    if (account) {
      tokens.setTokens('access', 'refresh');
      tokens.setUser(account);
    }
    const state = TestBed.runInInjectionContext(() => injectForumWriteState());
    // Let the effects (and afterNextRender) run.
    TestBed.tick();
    await Promise.resolve();
    capabilityCalls = http.match('/api/v1/auth/capabilities').map((req) => req.flush({ emailEnabled })).length;
    TestBed.tick();
    await Promise.resolve();
    const reloads = http.match('/api/v1/users/id/1');
    userReloads = reloads.length;
    reloads.forEach((req) =>
      fresh === null ? req.flush(null, { status: 500, statusText: 'x' }) : req.flush(fresh ?? account),
    );
    TestBed.tick();
    return state();
  }

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('is anonymous without a session', async () => {
    expect(await stateFor(null)).toBe('anonymous');
  });

  it('lets a student write', async () => {
    expect(await stateFor(user('ROLE_STUDENT'))).toBe('writer');
  });

  it('is read-only for an administrator', async () => {
    expect(await stateFor(user('ROLE_ADMIN'))).toBe('readOnly');
  });

  it('keeps pending and rejected experts apart', async () => {
    expect(await stateFor(user('ROLE_EXPERT', { expertStatus: 'PENDING' as ExpertStatus }))).toBe('pending');
    TestBed.resetTestingModule();
    expect(await stateFor(user('ROLE_EXPERT', { expertStatus: 'REJECTED' as ExpertStatus }))).toBe('rejected');
  });

  describe('unverified e-mail', () => {
    it('blocks a student whose e-mail is unverified when mail is enabled', async () => {
      expect(await stateFor(user('ROLE_STUDENT', { emailVerified: false }), true)).toBe('unverified');
    });

    it('does not block when mail is disabled (the API does not require verification then)', async () => {
      expect(await stateFor(user('ROLE_STUDENT', { emailVerified: false }), false)).toBe('writer');
    });

    it('does not block a verified student, and asks nothing of the server', async () => {
      expect(await stateFor(user('ROLE_STUDENT', { emailVerified: true }))).toBe('writer');
      expect(capabilityCalls).toBe(0);
    });

    it('does not block a session that does not say (older payload)', async () => {
      expect(await stateFor(user('ROLE_STUDENT'))).toBe('writer');
    });

    it('blocks an approved expert whose e-mail is unverified', async () => {
      expect(await stateFor(user('ROLE_EXPERT', { expertStatus: 'APPROVED' as ExpertStatus, emailVerified: false }))).toBe('unverified');
    });

    it('lets a pending or rejected expert see their own reason first', async () => {
      expect(await stateFor(user('ROLE_EXPERT', { expertStatus: 'PENDING' as ExpertStatus, emailVerified: false }))).toBe('pending');
      TestBed.resetTestingModule();
      expect(await stateFor(user('ROLE_EXPERT', { expertStatus: 'REJECTED' as ExpertStatus, emailVerified: false }))).toBe('rejected');
    });

    it('keeps an administrator read-only', async () => {
      expect(await stateFor(user('ROLE_ADMIN', { emailVerified: false }))).toBe('readOnly');
    });

    it('stays anonymous when signed out, whatever the capabilities say', async () => {
      expect(await stateFor(null, true)).toBe('anonymous');
    });

    it('asks the server again once, because the stored flag may be stale (confirmed on another device)', async () => {
      const stale = user('ROLE_STUDENT', { emailVerified: false });
      fresh = undefined;
      // Confirmed meanwhile: the reload says so.
      const state = await (async () => {
        localStorage.clear();
        TestBed.configureTestingModule({
          providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
        });
        http = TestBed.inject(HttpTestingController);
        const tokens = TestBed.inject(TokenService);
        tokens.setTokens('access', 'refresh');
        tokens.setUser(stale);
        const s = TestBed.runInInjectionContext(() => injectForumWriteState());
        TestBed.tick();
        await Promise.resolve();
        http.match('/api/v1/auth/capabilities').forEach((req) => req.flush({ emailEnabled: true }));
        TestBed.tick();
        await Promise.resolve();
        const reload = http.expectOne('/api/v1/users/id/1');
        expect(s()).toBe('unverified');
        reload.flush({ ...stale, emailVerified: true });
        TestBed.tick();
        return s;
      })();

      expect(state()).toBe('writer');
      TestBed.tick();
      http.expectNone('/api/v1/users/id/1');
    });

    it('reloads the user once when the state is unverified, and stays unverified if the server agrees', async () => {
      expect(await stateFor(user('ROLE_STUDENT', { emailVerified: false }), true)).toBe('unverified');
      expect(userReloads).toBe(1);
    });

    it('stays unverified when the reload fails', async () => {
      fresh = null;
      localStorage.clear();
      TestBed.configureTestingModule({
        providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
      });
      http = TestBed.inject(HttpTestingController);
      const tokens = TestBed.inject(TokenService);
      tokens.setTokens('access', 'refresh');
      tokens.setUser(user('ROLE_STUDENT', { emailVerified: false }));
      const state = TestBed.runInInjectionContext(() => injectForumWriteState());
      TestBed.tick();
      await Promise.resolve();
      http.match('/api/v1/auth/capabilities').forEach((req) => req.flush({ emailEnabled: true }));
      TestBed.tick();
      await Promise.resolve();
      http.expectOne('/api/v1/users/id/1').flush(null, { status: 500, statusText: 'x' });
      TestBed.tick();
      expect(state()).toBe('unverified');
    });

    it('does not reload the user when mail is disabled, or when the account is verified', async () => {
      expect(await stateFor(user('ROLE_STUDENT', { emailVerified: false }), false)).toBe('writer');
      expect(userReloads).toBe(0);
      TestBed.resetTestingModule();
      expect(await stateFor(user('ROLE_STUDENT', { emailVerified: true }))).toBe('writer');
      expect(userReloads).toBe(0);
    });

    it('becomes a writer as soon as the account is verified', async () => {
      localStorage.clear();
      TestBed.configureTestingModule({
        providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
      });
      http = TestBed.inject(HttpTestingController);
      const tokens = TestBed.inject(TokenService);
      tokens.setTokens('access', 'refresh');
      tokens.setUser(user('ROLE_STUDENT', { emailVerified: false }));
      const state = TestBed.runInInjectionContext(() => injectForumWriteState());
      TestBed.tick();
      await Promise.resolve();
      http.match('/api/v1/auth/capabilities').forEach((req) => req.flush({ emailEnabled: true }));
      TestBed.tick();
      await Promise.resolve();
      http.match('/api/v1/users/id/1').forEach((req) => req.flush(user('ROLE_STUDENT', { emailVerified: false })));
      TestBed.tick();
      expect(state()).toBe('unverified');

      tokens.setUser(user('ROLE_STUDENT', { emailVerified: true }));
      TestBed.tick();
      expect(state()).toBe('writer');
    });
  });
});
