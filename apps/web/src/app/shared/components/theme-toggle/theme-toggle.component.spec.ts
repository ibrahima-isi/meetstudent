import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { ThemeChoice, ThemeService } from '@services/theme.service';
import { ThemeToggleComponent } from './theme-toggle.component';

describe('ThemeToggleComponent', () => {
  let fixture: ComponentFixture<ThemeToggleComponent>;
  let choice: ReturnType<typeof signal<ThemeChoice>>;
  let set: jasmine.Spy;

  function button(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button');
  }

  async function render() {
    fixture = TestBed.createComponent(ThemeToggleComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    choice = signal<ThemeChoice>('system');
    set = jasmine.createSpy('set').and.callFake((c: ThemeChoice) => choice.set(c));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideTransloco(translocoOptions),
        { provide: ThemeService, useValue: { choice: choice.asReadonly(), set } },
      ],
    });
    await firstValueFrom(TestBed.inject(TranslocoService).load('en'));
    TestBed.inject(TranslocoService).setActiveLang('en');
  });

  it('is a labelled button naming the current theme and the next one', async () => {
    await render();

    expect(button().type).toBe('button');
    expect(button().getAttribute('aria-label')).toBe('Theme: system. Switch to light.');
  });

  it('cycles system, light, dark, system', async () => {
    await render();

    button().click();
    expect(set).toHaveBeenCalledWith('light');
    button().click();
    expect(set).toHaveBeenCalledWith('dark');
    button().click();
    expect(set).toHaveBeenCalledWith('system');
  });

  it('reflects the service state', async () => {
    choice.set('dark');
    await render();

    expect(button().getAttribute('aria-label')).toBe('Theme: dark. Switch to system.');
    expect(button().dataset['theme']).toBe('dark');

    choice.set('light');
    fixture.detectChanges();
    expect(button().getAttribute('aria-label')).toBe('Theme: light. Switch to dark.');
  });
});
