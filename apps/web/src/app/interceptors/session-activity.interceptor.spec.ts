import { DOCUMENT } from '@angular/common';
import { PLATFORM_ID, inject, provideEnvironmentInitializer, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TokenService } from '@services/token.service';
import { SessionActivityService } from '@services/session-activity.service';
import { sessionActivityInterceptor } from './session-activity.interceptor';
import { environment } from '../../environments/environment';

describe('session activity at the HTTP boundary', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  const listeners = new Map<string, EventListener>();
  const resource = `${environment.apiUrl}/schools`;

  function setup(platform = 'browser'): void {
    localStorage.clear();
    listeners.clear();
    const document = window.document;
    spyOn(document, 'addEventListener').and.callFake((type: string, listener: EventListenerOrEventListenerObject | null) => {
      if (typeof listener === 'function') listeners.set(type, listener);
    });
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: platform },
        { provide: DOCUMENT, useValue: document },
        
        provideEnvironmentInitializer(() => inject(SessionActivityService)),
        provideHttpClient(withInterceptors([sessionActivityInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(TokenService).setTokens('access', 'refresh');
  }

  function read(url = resource): string | null {
    http.get(url).subscribe();
    const request = backend.expectOne(url);
    const header = request.request.headers.get('X-Session-Activity');
    request.flush([]);
    return header;
  }

  // The browser adapter supplies trusted events; synthetic dispatchEvent does not.
  function interact(type = 'click', trusted = true): void {
    listeners.get(type)?.({ isTrusted: trusted } as Event);
  }

  afterEach(() => {
    backend?.verify();
    localStorage.clear();
  });

  it('does not count the initial load or automatic reads', () => {
    setup();
    expect(read()).toBeNull();
    expect(read()).toBeNull();
  });

  it('counts one authenticated business read after a real interaction', () => {
    setup();
    interact();
    expect(read()).toBe('true');
    expect(read()).toBeNull();
  });

  it('counts a keyboard search arriving after its debounce', () => {
    jasmine.clock().install();
    try {
      jasmine.clock().mockDate(new Date('2026-10-14T12:00:00Z'));
      setup();
      interact('input');
      jasmine.clock().tick(300);
      expect(read()).toBe('true');
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('does not keep an unused intent alive after one second', () => {
    jasmine.clock().install();
    try {
      jasmine.clock().mockDate(new Date('2026-10-14T12:00:00Z'));
      setup();
      interact('keydown');
      jasmine.clock().tick(1001);
      expect(read()).toBeNull();
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('ignores synthetic events', () => {
    setup();
    interact('click', false);
    expect(read()).toBeNull();
  });

  it('does not count auth endpoints, assets or external requests', () => {
    setup();
    interact();
    expect(read(`${environment.apiUrl}/auth/capabilities`)).toBeNull();
    expect(read('/assets/data.json')).toBeNull();
    expect(read('https://outside.test/api/v1/schools')).toBeNull();
    expect(read()).toBe('true');
  });

  it('does not count anonymous reads', () => {
    setup();
    TestBed.inject(TokenService).clear();
    interact();
    expect(read()).toBeNull();
  });

  it('never installs browser listeners or adds activity during SSR', () => {
    setup('server');
    expect(listeners.size).toBe(0);
    expect(read()).toBeNull();
  });
});
