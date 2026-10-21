import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, Injector, afterNextRender, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { TranslocoDirective } from '@jsverse/transloco';
import { AlertCircle, Flag, Loader2, LucideAngularModule } from 'lucide-angular';
import { FORUM_MAX_REASON } from '@models/forum';
import { ForumService } from '@services/forum.service';
import { noControlChars, notBlank } from './forum-composer.component';
import { ForumFailure, classifyForumError } from './forum-errors';

let nextId = 0;

/** The API strips the reason, then limits it: the limit applies to the trimmed text. */
const maxTrimmed = (max: number) => (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length > max ? { maxlength: true } : null;

/**
 * "Report" button of a thread or a reply, with its reason form. The reason is plain text, sent as typed (trimmed) and
 * never shown back. Outcomes that end the matter (reported, already reported or own message, message gone) close the
 * form and are announced in a polite status region; the others keep the form and the text, with an alert.
 */
@Component({
  selector: 'app-forum-report',
  imports: [ReactiveFormsModule, TranslocoDirective, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './forum-report.component.html',
})
export class ForumReportComponent {
  private readonly forum = inject(ForumService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  readonly kind = input.required<'thread' | 'reply'>();
  readonly targetId = input.required<number>();

  protected readonly AlertCircle = AlertCircle;
  protected readonly Flag = Flag;
  protected readonly Loader2 = Loader2;
  protected readonly max = FORUM_MAX_REASON;
  protected readonly uid = `forum-report-${nextId++}`;

  protected readonly form = inject(NonNullableFormBuilder).group({
    reason: ['', [Validators.required, notBlank, maxTrimmed(FORUM_MAX_REASON), noControlChars]],
  });
  private readonly reasonValue = toSignal(this.form.controls.reason.valueChanges, { initialValue: '' });
  protected readonly count = computed(() => this.reasonValue().trim().length);

  protected readonly expanded = signal(false);
  protected readonly sending = signal(false);
  /** `reported`: sent, or already reported / own message (409). `gone`: the message no longer exists (404). */
  protected readonly result = signal<'reported' | 'conflict' | 'gone' | null>(null);
  protected readonly failure = signal<ForumFailure | null>(null);
  private readonly refused = signal(false);
  protected readonly cooldown = signal(0);
  private cooldownTimer: ReturnType<typeof setInterval> | null = null;

  protected readonly reported = computed(() => this.result() === 'reported' || this.result() === 'conflict');
  protected readonly messageKey = computed(() => {
    switch (this.result()) {
      case 'reported':
        return 'forum.report.thanks';
      case 'conflict':
        return 'forum.report.conflict';
      case 'gone':
        return 'forum.report.gone';
      default:
        return '';
    }
  });

  protected readonly alert = computed(() => {
    const failure = this.failure();
    if (!failure || failure.kind === 'fields') return null;
    switch (failure.kind) {
      case 'rateLimited':
        return failure.seconds === null
          ? { key: 'forum.report.errors.rateLimitedNoDelay', params: {} }
          : { key: 'forum.report.errors.rateLimited', params: { seconds: failure.seconds } };
      case 'signedOut':
        return { key: 'forum.errors.signedOut', params: {} };
      case 'forbidden':
        return { key: 'forum.report.errors.forbidden', params: {} };
      default:
        return { key: 'forum.report.errors.failed', params: {} };
    }
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.stopCooldown());
    this.form.controls.reason.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.refused.set(false));
  }

  protected toggle(): void {
    if (this.reported()) return;
    if (this.expanded()) {
      this.close();
      return;
    }
    this.expanded.set(true);
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('[data-testid="forum-report-reason"]')?.focus(), { injector: this.injector });
  }

  protected close(): void {
    this.expanded.set(false);
    this.failure.set(null);
    this.focusOpen();
  }

  protected errorKey(): { key: string; max?: number } | null {
    if (this.refused()) return { key: 'invalid' };
    const control = this.form.controls.reason;
    if (!control.touched || control.valid) return null;
    if (control.hasError('required')) return { key: 'required' };
    if (control.hasError('maxlength')) return { key: 'tooLong', max: this.max };
    return { key: 'invalid' };
  }

  protected submit(): void {
    if (this.sending() || this.cooldown() > 0) return;
    this.failure.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.focusReason();
      return;
    }
    const reason = this.form.getRawValue().reason.trim();
    this.sending.set(true);
    const call = this.kind() === 'thread' ? this.forum.reportThread(this.targetId(), reason) : this.forum.reportReply(this.targetId(), reason);
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.finish('reported'),
      error: (err: unknown) => this.fail(err),
    });
  }

  private finish(result: 'reported' | 'conflict' | 'gone'): void {
    this.sending.set(false);
    this.result.set(result);
    this.expanded.set(false);
    this.form.reset();
    this.failure.set(null);
    // 409 and 404 change or remove the button: the announcement is where the focus goes.
    if (result === 'reported') this.focusOpen();
    else this.focusLive();
  }

  private fail(err: unknown): void {
    this.sending.set(false);
    if (err instanceof HttpErrorResponse && err.status === 409) {
      this.finish('conflict');
      return;
    }
    const failure = classifyForumError(err);
    if (failure.kind === 'notFound') {
      this.finish('gone');
      return;
    }
    if (failure.kind === 'fields') {
      this.refused.set(true);
      this.focusReason();
    }
    this.failure.set(failure);
    if (failure.kind === 'rateLimited' && failure.seconds) this.startCooldown(failure.seconds);
  }

  private startCooldown(seconds: number): void {
    this.stopCooldown();
    this.cooldown.set(seconds);
    this.cooldownTimer = setInterval(() => {
      this.cooldown.update((n) => n - 1);
      if (this.cooldown() <= 0) {
        this.stopCooldown();
        if (this.failure()?.kind === 'rateLimited') this.failure.set(null);
      }
    }, 1000);
  }

  private stopCooldown(): void {
    if (this.cooldownTimer !== null) clearInterval(this.cooldownTimer);
    this.cooldownTimer = null;
    this.cooldown.set(0);
  }

  private focusOpen(): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('[data-testid="forum-report-open"]')?.focus(), { injector: this.injector });
  }

  private focusLive(): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('[data-testid="forum-report-live"]')?.focus(), { injector: this.injector });
  }

  private focusReason(): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('[aria-invalid=true]')?.focus(), { injector: this.injector });
  }
}
