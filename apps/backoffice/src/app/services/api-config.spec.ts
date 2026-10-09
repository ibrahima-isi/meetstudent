import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { API_URL, SERVER_URL, serverUrlFrom } from './api-config';

describe('serverUrlFrom', () => {
  it('strips the /api/v1 suffix and trailing slashes', () => {
    expect(serverUrlFrom('http://localhost:8080/api/v1')).toBe('http://localhost:8080');
    expect(serverUrlFrom('https://api.x.io/api/v1/')).toBe('https://api.x.io');
  });

  it('is empty (same origin) for a relative API url', () => {
    expect(serverUrlFrom('/api/v1')).toBe('');
  });
});

describe('SERVER_URL', () => {
  it('is derived from API_URL by default', () => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), { provide: API_URL, useValue: 'http://a.test/api/v1' }],
    });
    expect(TestBed.inject(SERVER_URL)).toBe('http://a.test');
  });

  it('can be overridden', () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: API_URL, useValue: 'http://a.test/api/v1' },
        { provide: SERVER_URL, useValue: 'http://cdn.test' },
      ],
    });
    expect(TestBed.inject(SERVER_URL)).toBe('http://cdn.test');
  });
});
