import { Component, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { API_URL } from '@services/api-config';
import { Shell } from './shell';

@Component({ template: '' })
class Blank {}

describe('Shell (responsive menu)', () => {
  let fixture: ComponentFixture<Shell>;
  let router: Router;

  const el = () => fixture.nativeElement as HTMLElement;
  const toggle = () => el().querySelector<HTMLButtonElement>('[data-menu-toggle]')!;
  const sidebar = () => el().querySelector<HTMLElement>('#main-sidebar')!;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://api.test/api/v1' },
        provideRouter([
          { path: 'moderation', component: Blank },
          { path: 'schools', component: Blank },
        ]),
      ],
    });
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('has a hamburger button that is only shown below md and controls the sidebar', () => {
    expect(toggle()).not.toBeNull();
    expect(toggle().classList).toContain('md:hidden');
    expect(toggle().getAttribute('aria-controls')).toBe('main-sidebar');
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
    expect(sidebar().classList).toContain('md:block');
  });

  it('keeps the sidebar hidden below md until the toggle is clicked', () => {
    expect(sidebar().classList).toContain('hidden');
    toggle().click();
    fixture.detectChanges();
    expect(toggle().getAttribute('aria-expanded')).toBe('true');
    expect(sidebar().classList).not.toContain('hidden');
    expect(el().querySelector('[data-backdrop]')).not.toBeNull();
    toggle().click();
    fixture.detectChanges();
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
    expect(el().querySelector('[data-backdrop]')).toBeNull();
  });

  it('closes on Escape and gives focus back to the toggle', () => {
    toggle().click();
    fixture.detectChanges();
    el().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(toggle());
  });

  it('closes on navigation', async () => {
    toggle().click();
    fixture.detectChanges();
    await router.navigateByUrl('/schools');
    fixture.detectChanges();
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
  });

  it('closes when the backdrop is clicked', () => {
    toggle().click();
    fixture.detectChanges();
    el().querySelector<HTMLElement>('[data-backdrop]')!.click();
    fixture.detectChanges();
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
  });
});
