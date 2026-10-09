import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { ErrorStateComponent } from './error-state.component';

describe('ErrorStateComponent', () => {
  async function render(lang: 'fr' | 'en' = 'en') {
    TestBed.configureTestingModule({
      imports: [ErrorStateComponent],
      providers: [provideZonelessChangeDetection(), provideTransloco(translocoOptions)],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    const fixture = TestBed.createComponent(ErrorStateComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('announces itself as an alert with a default translated message', async () => {
    const fixture = await render('en');
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[role="alert"]')).not.toBeNull();
    expect(el.textContent).toContain('Something went wrong');
  });

  it('is translated in French', async () => {
    const fixture = await render('fr');

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Une erreur est survenue');
  });

  it('shows a custom message when given one', async () => {
    const fixture = await render('en');
    fixture.componentRef.setInput('message', 'Schools could not be loaded');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Schools could not be loaded');
  });

  it('emits retry when the button is clicked', async () => {
    const fixture = await render('en');
    let retries = 0;
    fixture.componentInstance.retry.subscribe(() => retries++);

    (fixture.nativeElement as HTMLElement).querySelector('button')!.click();

    expect(retries).toBe(1);
  });
});
