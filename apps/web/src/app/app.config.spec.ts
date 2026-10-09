import { TestBed } from '@angular/core/testing';
import { HttpBackend, HttpXhrBackend } from '@angular/common/http';
import { appConfig } from './app.config';

describe('appConfig', () => {
  it('sends HTTP through XHR: the fetch backend emits no upload progress events', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(HttpBackend)).toBeInstanceOf(HttpXhrBackend);
  });
});
