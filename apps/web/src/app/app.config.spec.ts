import { TestBed } from '@angular/core/testing';
import { HttpBackend, HttpXhrBackend } from '@angular/common/http';
import { ViewportScroller } from '@angular/common';
import { appConfig } from './app.config';

describe('appConfig', () => {
  it('sends HTTP through XHR: the fetch backend emits no upload progress events', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(HttpBackend)).toBeInstanceOf(HttpXhrBackend);
  });

  it('scrolls anchors below the fixed Dock navbar, which the router does not get from CSS scroll-padding', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });
    TestBed.inject(ViewportScroller); // the router's scroller sets its offset when the environment starts

    const tall = document.createElement('div');
    tall.style.height = '4000px';
    const target = document.createElement('section');
    target.id = 'anchor-offset-target';
    target.style.height = '200px';
    const spacer = document.createElement('div');
    spacer.style.height = '4000px';
    document.body.append(tall, target, spacer);
    try {
      TestBed.inject(ViewportScroller).scrollToAnchor('anchor-offset-target');

      const top = target.getBoundingClientRect().top;
      expect(top).toBeGreaterThanOrEqual(90);
      expect(top).toBeLessThanOrEqual(102);
    } finally {
      tall.remove();
      target.remove();
      spacer.remove();
      window.scrollTo(0, 0);
    }
  });
});
