import { afterNextRender, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, signal } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { catchError, forkJoin, map, of } from 'rxjs';
import { LocaleService } from '@services/locale.service';
import { ProgramService } from '@services/program.service';
import { SchoolService } from '@services/school.service';
import { RevealDirective } from '@shared/directives/reveal.directive';
import { countUp } from '../count-up';

interface Figures {
  schools: number;
  programs: number;
}

const ZERO: Figures = { schools: 0, programs: 0 };
const DURATION_MS = 1200;

@Component({
  selector: 'app-key-figures',
  imports: [TranslocoDirective, RevealDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    @if (figures(); as f) {
      <section class="mx-auto max-w-5xl px-4 py-16" appReveal *transloco="let t">
        <h2 class="sr-only">{{ t('lp.figures.title') }}</h2>
        <dl class="grid gap-6 sm:grid-cols-2">
          <div class="rounded-2xl border border-border bg-card p-8 text-center">
            <dd class="text-5xl font-semibold tracking-tight text-brand">
              <span aria-hidden="true">{{ format(displayed()?.schools ?? f.schools) }}</span>
              <span class="sr-only">{{ format(f.schools) }}</span>
            </dd>
            <dt class="mt-2 text-muted-foreground">{{ t('lp.figures.schools') }}</dt>
          </div>
          <div class="rounded-2xl border border-border bg-card p-8 text-center">
            <dd class="text-5xl font-semibold tracking-tight text-brand">
              <span aria-hidden="true">{{ format(displayed()?.programs ?? f.programs) }}</span>
              <span class="sr-only">{{ format(f.programs) }}</span>
            </dd>
            <dt class="mt-2 text-muted-foreground">{{ t('lp.figures.programs') }}</dt>
          </div>
        </dl>
      </section>
    }
  `,
})
export class KeyFiguresComponent {
  private readonly locale = inject(LocaleService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly destroyRef = inject(DestroyRef);

  protected readonly figures = signal<Figures | null>(null);
  protected readonly displayed = signal<Figures | null>(null);

  private holdAtZero = false;
  private observer: IntersectionObserver | null = null;
  private cancels: (() => void)[] = [];

  constructor() {
    forkJoin({
      schools: inject(SchoolService).getSchools(0, 1),
      programs: inject(ProgramService).getPrograms(0, 1),
    })
      .pipe(
        map((pages): Figures => ({ schools: pages.schools.totalElements ?? 0, programs: pages.programs.totalElements ?? 0 })),
        catchError(() => of(null)),
      )
      .subscribe((f) => {
        if (!f || (f.schools === 0 && f.programs === 0)) {
          return;
        }
        this.figures.set(f);
        this.displayed.set(this.holdAtZero ? ZERO : f);
      });

    afterNextRender(() => this.setUpAnimation());
    this.destroyRef.onDestroy(() => this.teardown());
  }

  protected format(n: number): string {
    return n.toLocaleString(this.locale.active());
  }

  private setUpAnimation(): void {
    const canAnimate =
      typeof IntersectionObserver !== 'undefined' &&
      !(typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches) &&
      this.host.getBoundingClientRect().top >= innerHeight;
    if (!canAnimate) {
      return;
    }

    this.holdAtZero = true;
    if (this.figures()) {
      this.displayed.set(ZERO);
    }
    this.observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          this.start();
        }
      },
      { threshold: 0.3 },
    );
    this.observer.observe(this.host);
  }

  private start(): void {
    this.observer?.disconnect();
    this.observer = null;
    this.holdAtZero = false;
    const f = this.figures();
    if (!f) {
      return;
    }
    this.cancels = [
      countUp(f.schools, DURATION_MS, (schools) => this.displayed.update((d) => ({ ...(d ?? ZERO), schools }))),
      countUp(f.programs, DURATION_MS, (programs) => this.displayed.update((d) => ({ ...(d ?? ZERO), programs }))),
    ];
  }

  private teardown(): void {
    this.observer?.disconnect();
    this.observer = null;
    this.cancels.forEach((cancel) => cancel());
    this.cancels = [];
  }
}
