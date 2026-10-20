import { ChangeDetectionStrategy, Component, RESPONSE_INIT, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { ArrowLeft, Clock, Euro, GraduationCap, LucideAngularModule, Users } from 'lucide-angular';
import { EMPTY, Subject, catchError, combineLatest, map, of, startWith, switchMap, tap } from 'rxjs';
import { pluralKey } from '@i18n/plural';
import { Program, programLevelKey } from '@models/entities';
import { LocaleService } from '@services/locale.service';
import { ProgramService } from '@services/program.service';
import { PageTitleService } from '@shared/page-title';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';

/**
 * Public page of one programme. Every administrator-typed text (description of
 * the programme and of its courses) is interpolated, never bound as HTML, and
 * `whitespace-pre-line` keeps its line breaks.
 */
@Component({
  selector: 'app-program-detail-page',
  imports: [RouterLink, LucideAngularModule, TranslocoDirective, ErrorStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './program-detail-page.component.html',
})
export class ProgramDetailPageComponent {
  private readonly programService = inject(ProgramService);
  private readonly route = inject(ActivatedRoute);
  private readonly locale = inject(LocaleService);
  private readonly pageTitle = inject(PageTitleService);
  private readonly responseInit = inject(RESPONSE_INIT, { optional: true });

  protected readonly ArrowLeft = ArrowLeft;
  protected readonly Clock = Clock;
  protected readonly Euro = Euro;
  protected readonly GraduationCap = GraduationCap;
  protected readonly Users = Users;

  protected readonly program = signal<Program | null>(null);
  /** 'error' shows the retry surface, never placeholder data. */
  protected readonly status = signal<'loading' | 'loaded' | 'error' | 'notFound'>('loading');
  protected readonly lang = computed(() => this.locale.active());

  private readonly retry$ = new Subject<void>();

  constructor() {
    combineLatest([this.route.paramMap.pipe(map((params) => params.get('id') ?? '')), this.retry$.pipe(startWith(undefined))])
      .pipe(
        tap(() => this.status.set('loading')),
        switchMap(([id]) => {
          // Digits only: Number() would also accept "1e3" or "0x10".
          const numeric = /^\d{1,10}$/.test(id) ? Number(id) : 0;
          if (!Number.isSafeInteger(numeric) || numeric <= 0) {
            this.markNotFound();
            return EMPTY;
          }
          return this.programService.getProgram(numeric).pipe(
            catchError((error: { status?: number }) => {
              if (error?.status === 404) {
                this.markNotFound();
                return EMPTY;
              }
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((program) => {
        if (program === null) {
          this.fail();
          return;
        }
        this.program.set(program);
        this.pageTitle.set(program.name);
        this.status.set('loaded');
      });
  }

  /** Retry action of the error state. */
  protected load(): void {
    this.retry$.next();
  }

  /** Unknown programme: no retry, and HTTP 404 when server-rendered (the Response is built from RESPONSE_INIT after rendering). */
  private markNotFound(): void {
    if (this.responseInit) {
      this.responseInit.status = 404;
    }
    this.program.set(null);
    this.status.set('notFound');
  }

  private fail(): void {
    this.program.set(null);
    this.status.set('error');
  }

  protected readonly levelKey = programLevelKey;

  /** Euro amount in the format of the language being read. */
  protected fee(amount: number): string {
    return new Intl.NumberFormat(this.locale.active(), {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: Number.isInteger(amount) ? 0 : 2,
    }).format(amount);
  }

  protected plural(base: string, count: number): string {
    return pluralKey(base, count, this.locale.active());
  }
}
