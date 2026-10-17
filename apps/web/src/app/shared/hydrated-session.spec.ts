import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TokenService } from '@services/token.service';
import { injectHydratedSession } from './hydrated-session';

@Component({ template: '' })
class HostComponent {
  readonly session = injectHydratedSession();
}

describe('injectHydratedSession', () => {
  let authenticated: ReturnType<typeof signal<boolean>>;

  const create = async () => {
    const fixture = TestBed.createComponent(HostComponent);
    return { fixture, session: fixture.componentInstance.session };
  };

  beforeEach(() => {
    authenticated = signal(true);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: TokenService, useValue: { isAuthenticated: authenticated } },
      ],
    });
  });

  it('reads the session at once in a pure client bootstrap, which has no server markup to match', async () => {
    const { session } = await create();
    expect(session.hydrated()).toBeTrue();
    expect(session.signedIn()).toBeTrue();
  });

  it('follows the token after that, in both directions', async () => {
    const { session } = await create();
    authenticated.set(false);
    expect(session.signedIn()).toBeFalse();
    authenticated.set(true);
    expect(session.signedIn()).toBeTrue();
  });

  describe('on a server-rendered document', () => {
    beforeEach(() => document.body.setAttribute('ng-server-context', 'ssr'));
    afterEach(() => document.body.removeAttribute('ng-server-context'));

    it('claims nobody is signed in until the first render is done, so hydration matches the server', async () => {
      const { session } = await create();
      expect(session.hydrated()).toBeFalse();
      expect(session.signedIn()).toBeFalse();
    });

    it('reads the session once the first render is done', async () => {
      const { fixture, session } = await create();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(session.hydrated()).toBeTrue();
      expect(session.signedIn()).toBeTrue();
    });

    it('stays anonymous after hydration when there is no session', async () => {
      authenticated.set(false);
      const { fixture, session } = await create();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(session.hydrated()).toBeTrue();
      expect(session.signedIn()).toBeFalse();
    });
  });
});
