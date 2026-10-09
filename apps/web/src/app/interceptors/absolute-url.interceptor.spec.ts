import { PLATFORM_ID, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { absoluteUrlInterceptor } from './absolute-url.interceptor';

describe('absoluteUrlInterceptor', () => {
  function setup(platform: 'browser' | 'server') {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: platform },
        provideHttpClient(withInterceptors([absoluteUrlInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    return { http: TestBed.inject(HttpClient), mock: TestBed.inject(HttpTestingController) };
  }

  it('gives a relative API URL the page origin in the browser, so it matches the key the server cached', () => {
    const { http, mock } = setup('browser');

    http.get('/api/v1/schools').subscribe();

    mock.expectOne(`${location.origin}/api/v1/schools`);
  });

  it('leaves an absolute URL alone', () => {
    const { http, mock } = setup('browser');

    http.get('https://elsewhere.example/api/v1/schools').subscribe();

    mock.expectOne('https://elsewhere.example/api/v1/schools');
  });

  it('leaves a protocol-relative URL alone', () => {
    const { http, mock } = setup('browser');

    http.get('//cdn.example/x').subscribe();

    mock.expectOne('//cdn.example/x');
  });

  it('does nothing on the server, which has its own absolute API URL', () => {
    const { http, mock } = setup('server');

    http.get('/api/v1/schools').subscribe();

    mock.expectOne('/api/v1/schools');
  });
});
