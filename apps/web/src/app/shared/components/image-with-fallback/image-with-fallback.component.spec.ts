import { firstValueFrom } from 'rxjs';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { translocoOptions } from '@i18n/transloco.config';
import { ImageWithFallbackComponent } from './image-with-fallback.component';

describe('ImageWithFallbackComponent', () => {
  let transloco: TranslocoService;

  async function render(lang: 'fr' | 'en', alt?: string): Promise<ComponentFixture<ImageWithFallbackComponent>> {
    await firstValueFrom(transloco.load(lang));
    transloco.setActiveLang(lang);
    const fixture = TestBed.createComponent(ImageWithFallbackComponent);
    fixture.componentRef.setInput('src', 'broken.png');
    if (alt !== undefined) {
      fixture.componentRef.setInput('alt', alt);
    }
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  async function failToLoad(fixture: ComponentFixture<ImageWithFallbackComponent>): Promise<HTMLImageElement> {
    fixture.nativeElement.querySelector('img').dispatchEvent(new Event('error'));
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement.querySelector('img');
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ImageWithFallbackComponent],
      providers: [provideZonelessChangeDetection(), provideTransloco(translocoOptions)],
    }).compileComponents();
    transloco = TestBed.inject(TranslocoService);
  });

  it('keeps the passed alt until the image fails', async () => {
    const fixture = await render('fr', 'Harvard');

    expect(fixture.nativeElement.querySelector('img').getAttribute('alt')).toBe('Harvard');
  });

  it('reuses the passed alt on the fallback image', async () => {
    const fixture = await render('fr', 'Harvard');

    expect((await failToLoad(fixture)).getAttribute('alt')).toBe('Harvard');
  });

  it('keeps an explicit empty alt (decorative image) on the fallback image', async () => {
    const fixture = await render('fr', '');

    expect((await failToLoad(fixture)).getAttribute('alt')).toBe('');
  });

  it('describes the failure in French when no alt was passed', async () => {
    const fixture = await render('fr');

    expect((await failToLoad(fixture)).getAttribute('alt')).toBe("Impossible de charger l'image");
  });

  it('describes the failure in English when no alt was passed', async () => {
    const fixture = await render('en');

    expect((await failToLoad(fixture)).getAttribute('alt')).toBe('Image could not be loaded');
  });
});
