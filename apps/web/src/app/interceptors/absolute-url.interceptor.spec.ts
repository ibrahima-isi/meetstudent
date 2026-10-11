import { PLATFORM_ID, provideZonelessChangeDetection, REQUEST } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { absoluteUrlInterceptor } from './absolute-url.interceptor';

describe('absoluteUrlInterceptor', () => {
  function setup(platform: 'browser' | 'server', requestUrl?: string) {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: platform },
        ...(requestUrl ? [{ provide: REQUEST, useValue: new Request(requestUrl) }] : []),
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

  // Without API_URL (ng serve) the server would cache under the relative URL and the
  // browser, asking for the absolute one, would miss it and fetch everything again.
  it('gives a relative URL the origin of the page being rendered on the server, the key the browser will ask for', () => {
    const { http, mock } = setup('server', 'http://localhost:4200/fr/schools?q=x');

    http.get('/api/v1/schools').subscribe();

    mock.expectOne('http://localhost:4200/api/v1/schools');
  });

  it('leaves an absolute URL alone on the server, where API_URL already points at the API', () => {
    const { http, mock } = setup('server', 'http://localhost:4200/fr/schools');

    http.get('http://api:8080/api/v1/schools').subscribe();

    mock.expectOne('http://api:8080/api/v1/schools');
  });

  it('does nothing on the server when there is no request to learn the origin from', () => {
    const { http, mock } = setup('server');

    http.get('/api/v1/schools').subscribe();

    mock.expectOne('/api/v1/schools');
  });
});
