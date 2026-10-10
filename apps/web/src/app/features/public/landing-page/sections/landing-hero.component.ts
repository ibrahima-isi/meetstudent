import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { ArrowRight, GraduationCap, LucideAngularModule, MapPin, Search, Star } from 'lucide-angular';
import { MeshBackgroundComponent } from '@shared/components/mesh-background/mesh-background.component';
import { LocaleService } from '@services/locale.service';

@Component({
  selector: 'app-landing-hero',
  imports: [ReactiveFormsModule, RouterLink, TranslocoDirective, LucideAngularModule, MeshBackgroundComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block', '(pointermove)': 'tilt($event)', '(pointerleave)': 'resetTilt()' },
  styles: [
    `
      .stage-wrap { perspective: 1200px; }
      .stage { position: relative; height: 26rem; transform-style: preserve-3d;
        transform: rotateX(calc(var(--py, 0) * -6deg)) rotateY(calc(var(--px, 0) * 8deg)); transition: transform 0.2s ease-out; }
      .card { position: absolute; display: flex; flex-direction: column; gap: 0.6rem; padding: 1rem; border-radius: 1.25rem;
        background: var(--glass); border: 1px solid var(--glass-border); backdrop-filter: blur(14px); box-shadow: var(--shadow-glow); }
      .chip { display: inline-grid; place-items: center; width: 2.25rem; height: 2.25rem; border-radius: 0.75rem; background: var(--brand-soft); color: var(--brand-soft-foreground); }
      .line { display: block; height: 0.5rem; border-radius: 9999px; background: var(--muted); }
      .c1 { width: 15rem; top: 2rem; left: 6%; transform: translateZ(60px); }
      .c2 { width: 13rem; top: 10rem; right: 2%; transform: translateZ(110px) rotate(3deg); }
      .c3 { width: 14rem; bottom: 1rem; left: 22%; transform: translateZ(30px); }
      @media (max-width: 47.99rem) { .stage { height: 18rem; } .c3 { display: none; } .c1 { left: 0; } .c2 { right: 0; top: 7rem; } }
      @media (prefers-reduced-motion: reduce) { .stage { transform: none; transition: none; } }
    `,
  ],
  template: `
    <section class="relative isolate overflow-hidden" *transloco="let t">
      <app-mesh-background />
      <div class="mx-auto grid max-w-7xl items-center gap-12 px-4 pb-20 pt-32 sm:px-6 lg:grid-cols-2 lg:px-8 lg:pb-28 lg:pt-40">
        <div>
          <p class="mb-4 inline-flex rounded-full bg-brand-soft px-3 py-1 text-sm font-medium text-brand-soft-foreground">{{ t('lp.hero.eyebrow') }}</p>
          <h1 class="text-4xl font-semibold tracking-tight text-foreground sm:text-5xl lg:text-6xl">{{ t('lp.hero.title') }}</h1>
          <p class="mt-5 max-w-xl text-lg text-muted-foreground">{{ t('lp.hero.subtitle') }}</p>

          <form class="mt-8 flex max-w-xl items-center gap-2 rounded-full border border-border-strong bg-glass p-1.5 backdrop-blur" role="search" (submit)="onSubmit($event)">
            <label for="hero-search" class="sr-only">{{ t('lp.hero.searchLabel') }}</label>
            <lucide-icon [img]="Search" class="ml-3 h-5 w-5 shrink-0 text-muted-foreground" aria-hidden="true" />
            <input
              id="hero-search"
              type="search"
              maxlength="100"
              autocomplete="off"
              [formControl]="query"
              [placeholder]="t('lp.hero.searchPlaceholder')"
              class="min-w-0 flex-1 bg-transparent px-1 py-2 text-foreground placeholder:text-muted-foreground focus:outline-none"
            />
            <button type="submit" class="btn btn-primary">{{ t('lp.hero.searchSubmit') }}</button>
          </form>

          <div class="mt-6 flex flex-wrap gap-3">
            <a class="btn btn-primary btn-lg" [routerLink]="['/', lang(), 'schools']">
              {{ t('lp.hero.ctaExplore') }}
              <lucide-icon [img]="ArrowRight" class="h-4 w-4" aria-hidden="true" />
            </a>
            <a class="btn btn-secondary btn-lg" [routerLink]="['/', lang(), 'register']">{{ t('lp.hero.ctaRegister') }}</a>
          </div>
        </div>

        <div class="stage-wrap hidden md:block" aria-hidden="true">
          <div class="stage" [style.--px]="px()" [style.--py]="py()">
            <div class="card c1 animate-float">
              <span class="chip"><lucide-icon [img]="GraduationCap" class="h-5 w-5" /></span>
              <span class="line w-3/4"></span><span class="line w-1/2"></span>
            </div>
            <div class="card c2 animate-float" style="animation-delay: -2s">
              <div class="flex gap-1 text-yellow-400">
                @for (n of [1, 2, 3, 4, 5]; track n) { <lucide-icon [img]="Star" class="h-4 w-4 fill-yellow-400" /> }
              </div>
              <span class="line w-full"></span><span class="line w-2/3"></span>
            </div>
            <div class="card c3 animate-float" style="animation-delay: -4s">
              <span class="chip"><lucide-icon [img]="MapPin" class="h-5 w-5" /></span>
              <span class="line w-2/3"></span><span class="line w-1/3"></span>
            </div>
          </div>
        </div>
      </div>
    </section>
  `,
})
export class LandingHeroComponent {
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  protected readonly lang = inject(LocaleService).active;

  protected readonly Search = Search;
  protected readonly ArrowRight = ArrowRight;
  protected readonly GraduationCap = GraduationCap;
  protected readonly MapPin = MapPin;
  protected readonly Star = Star;

  protected readonly query = new FormControl('', { nonNullable: true });
  protected readonly px = signal(0);
  protected readonly py = signal(0);

  protected onSubmit(event: Event): void {
    event.preventDefault();
    const q = this.query.value.trim();
    void this.router.navigate(['/', this.lang(), 'schools'], { queryParams: q ? { q } : {} });
  }

  protected tilt(event: PointerEvent): void {
    if (event.pointerType !== 'mouse' || this.prefersReducedMotion()) {
      return;
    }
    const rect = this.host.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) {
      return;
    }
    this.px.set(((event.clientX - rect.left) / rect.width - 0.5) * 2);
    this.py.set(((event.clientY - rect.top) / rect.height - 0.5) * 2);
  }

  protected resetTilt(): void {
    this.px.set(0);
    this.py.set(0);
  }

  private prefersReducedMotion(): boolean {
    return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
}
