import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { AuthLayoutComponent } from './auth-layout.component';

@Component({ template: '<p id="child">child</p>' })
class ChildComponent {}

describe('AuthLayoutComponent', () => {
  let fixture: ComponentFixture<AuthLayoutComponent>;
  let root: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{ path: '', component: AuthLayoutComponent, children: [{ path: '', component: ChildComponent }] }]),
        provideTransloco(translocoOptions),
        { provide: LocaleService, useValue: { active: signal('fr'), remember: () => undefined } },
      ],
    });
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
    fixture = TestBed.createComponent(AuthLayoutComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    root = fixture.nativeElement;
  });

  it('links back to the home page of the current language', () => {
    const link = root.querySelector('a[href="/fr"]');
    expect(link?.textContent).toContain("Retour à l'accueil");
  });

  it('shows the brand panel on the left, hidden below the lg breakpoint', () => {
    const panel = root.querySelector('app-auth-brand-panel');
    expect(panel).toBeTruthy();
    const wrapper = panel!.parentElement!;
    expect(wrapper.classList).toContain('hidden');
    expect(wrapper.classList).toContain('lg:flex');
    expect(wrapper.parentElement!.children[0]).toBe(wrapper);
  });

  it('renders the routed form in the second column', async () => {
    await TestBed.inject(Router).navigateByUrl('/');
    fixture.detectChanges();
    const grid = root.querySelector('.grid')!;
    expect(grid.children[1].querySelector('router-outlet')).toBeTruthy();
  });
});
