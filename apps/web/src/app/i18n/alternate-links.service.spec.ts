import { TestBed } from '@angular/core/testing';
import { DOCUMENT } from '@angular/common';
import { Component, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AlternateLinksService } from './alternate-links.service';

@Component({ template: 'ok' })
class BlankComponent {}

describe('AlternateLinksService', () => {
  let service: AlternateLinksService;
  let document: Document;

  /**
   * The real document, not a fake: the service exists to write into `<head>`,
   * and Karma runs in a real Chrome, so asserting on the actual DOM is both
   * simpler and closer to what ships. `origin` therefore comes from Karma's own
   * URL rather than being hardcoded.
   */
  function alternates(): { hreflang: string; href: string }[] {
    return Array.from(
      document.head.querySelectorAll<HTMLLinkElement>('link[rel="alternate"]'),
    ).map((link) => ({
      hreflang: link.getAttribute('hreflang') ?? '',
      href: link.getAttribute('href') ?? '',
    }));
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{ path: '**', component: BlankComponent }]),
      ],
    });
    service = TestBed.inject(AlternateLinksService);
    document = TestBed.inject(DOCUMENT);
  });

  afterEach(() => {
    document.head
      .querySelectorAll('link[rel="alternate"]')
      .forEach((link) => link.remove());
  });

  it('advertises both locales and an x-default pointing at French', () => {
    service.update('/fr/login');

    const origin = document.location.origin;
    expect(alternates()).toEqual([
      { hreflang: 'fr', href: `${origin}/fr/login` },
      { hreflang: 'en', href: `${origin}/en/login` },
      { hreflang: 'x-default', href: `${origin}/fr/login` },
    ]);
  });

  it('describes the same page whichever locale it was reached in', () => {
    service.update('/en/login');

    const origin = document.location.origin;
    expect(alternates()).toEqual([
      { hreflang: 'fr', href: `${origin}/fr/login` },
      { hreflang: 'en', href: `${origin}/en/login` },
      { hreflang: 'x-default', href: `${origin}/fr/login` },
    ]);
  });

  it('rewrites the tags on the next navigation instead of appending', () => {
    service.update('/fr/login');
    service.update('/en/register');

    expect(alternates().length).toBe(3);
    expect(alternates()[1].href).toBe(`${document.location.origin}/en/register`);
  });

  it('keeps query parameters and the fragment, which identify the page too', () => {
    service.update('/fr/schools/7?tab=programs#rates');

    expect(alternates()[1].href).toBe(
      `${document.location.origin}/en/schools/7?tab=programs#rates`,
    );
  });

  it('emits no trailing slash at a locale root', () => {
    service.update('/fr');

    const origin = document.location.origin;
    expect(alternates().map((a) => a.href)).toEqual([
      `${origin}/fr`,
      `${origin}/en`,
      `${origin}/fr`,
    ]);
  });

  it('treats an un-prefixed path as one that lost its prefix', () => {
    // The guard redirects these before they settle, so it should not happen —
    // but guessing wrong here would publish two URLs that 404.
    service.update('/login');

    expect(alternates()[0].href).toBe(`${document.location.origin}/fr/login`);
  });

  it('follows the router once started', async () => {
    service.start();
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/fr/login');
    expect(alternates()[0].href).toBe(`${document.location.origin}/fr/login`);

    await harness.navigateByUrl('/en/register');
    expect(alternates().length).toBe(3);
    expect(alternates()[1].href).toBe(`${document.location.origin}/en/register`);
  });
});
