import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, Injector, OnInit, afterNextRender, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { TranslocoDirective } from '@jsverse/transloco';
import { AlertCircle, Loader2, LucideAngularModule } from 'lucide-angular';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { FORUM_MAX_BODY, FORUM_MAX_TITLE, ForumReply, ForumThread } from '@models/forum';
import { ForumService } from '@services/forum.service';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { ForumField, ForumFailure, classifyForumError } from './forum-errors';

/** Every control character but tab, CR and LF: the server refuses them. */
export const CONTROL_CHARS = /[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/;

export const notBlank = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim() === '' ? { required: true } : null;

export const noControlChars = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && CONTROL_CHARS.test(control.value) ? { invalid: true } : null;

export interface ComposerExisting {
  id: number;
  title?: string;
  body: string;
}

export interface ComposerSchool {
  id: number;
  name: string;
}

let nextId = 0;

/**
 * Writes a forum message: a new thread (title, message, optional school), a reply, or the edit of one of them.
 * It calls the API itself and reports the saved message. Text goes to the server as typed (trimmed): plain text only.
 */
@Component({
  selector: 'app-forum-composer',
  imports: [ReactiveFormsModule, RouterLink, TranslocoDirective, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './forum-composer.component.html',
})
export class ForumComposerComponent implements OnInit {
  private readonly forum = inject(ForumService);
  private readonly tokens = inject(TokenService);
  private readonly locale = inject(LocaleService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  readonly kind = input.required<'thread' | 'reply'>();
  /** The thread a new reply goes to. */
  readonly threadId = input<number>();
  /** A message being edited; absent for a new one. */
  readonly existing = input<ComposerExisting>();
  /** Schools offered on a new thread; none hides the choice. */
  readonly schools = input<ComposerSchool[]>([]);
  /** Shows a Cancel button on a new message too (an edit always has one). */
  readonly cancellable = input(false);

  readonly savedThread = output<ForumThread>();
  readonly savedReply = output<ForumReply>();
  readonly cancelled = output<void>();
  readonly locked = output<void>();

  protected readonly AlertCircle = AlertCircle;
  protected readonly Loader2 = Loader2;
  protected readonly max = { title: FORUM_MAX_TITLE, body: FORUM_MAX_BODY } as const;
  protected readonly uid = `forum-form-${nextId++}`;

  protected readonly form = inject(NonNullableFormBuilder).group({
    title: ['', [Validators.required, notBlank, Validators.maxLength(FORUM_MAX_TITLE), noControlChars]],
    body: ['', [Validators.required, notBlank, Validators.maxLength(FORUM_MAX_BODY), noControlChars]],
    schoolId: [''],
  });

  private readonly titleValue = toSignal(this.form.controls.title.valueChanges, { initialValue: '' });
  private readonly bodyValue = toSignal(this.form.controls.body.valueChanges, { initialValue: '' });
  protected readonly titleCount = computed(() => this.titleValue().length);
  protected readonly bodyCount = computed(() => this.bodyValue().length);

  protected readonly sending = signal(false);
  /** Seconds left before another try, after a 429 that said how long to wait. */
  protected readonly cooldown = signal(0);
  private cooldownTimer: ReturnType<typeof setInterval> | null = null;
  protected readonly failure = signal<ForumFailure | null>(null);
  /** Fields the server refused; each clears as soon as it is edited. */
  private readonly serverFields = signal<Partial<Record<ForumField, true>>>({});

  protected readonly showTitle = computed(() => this.kind() === 'thread');
  protected readonly showSchool = computed(() => this.kind() === 'thread' && !this.existing() && this.schools().length > 0);
  protected readonly showCancel = computed(() => !!this.existing() || this.cancellable());

  ngOnInit(): void {
    if (this.kind() === 'reply') {
      this.form.controls.title.clearValidators();
      this.form.controls.title.updateValueAndValidity();
    }
    const existing = this.existing();
    if (existing) {
      this.form.patchValue({ title: existing.title ?? '', body: existing.body });
      // An edit opens in place of the text it changes: put the cursor where the writing happens.
      afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('[data-testid="forum-title"], [data-testid="forum-body"]')?.focus(), {
        injector: this.injector,
      });
    }
    this.destroyRef.onDestroy(() => this.stopCooldown());
    for (const field of ['title', 'body', 'schoolId'] as const) {
      this.form.controls[field].valueChanges.subscribe(() => {
        if (this.serverFields()[field]) this.serverFields.update(({ [field]: _cleared, ...rest }) => rest);
      });
    }
  }

  /** The message to show under a field, or null while it is fine or not yet visited. */
  protected schoolRefused(): boolean {
    return !!this.serverFields().schoolId;
  }

  protected errorFor(field: 'title' | 'body'): { key: string; max?: number } | null {
    if (this.serverFields()[field]) return { key: 'invalid' };
    const control = this.form.controls[field];
    if (!control.touched || control.valid) return null;
    if (control.hasError('required')) return { key: 'required' };
    if (control.hasError('maxlength')) return { key: 'tooLong', max: this.max[field] };
    return { key: 'invalid' };
  }

  protected describedBy(field: 'title' | 'body'): string | null {
    const ids = [this.errorFor(field) ? `${this.uid}-${field}-error` : '', `${this.uid}-${field}-count`];
    return ids.filter(Boolean).join(' ') || null;
  }

  protected submit(): void {
    if (this.sending() || this.cooldown() > 0) return;
    this.failure.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.focusFirstInvalid();
      return;
    }

    const value = this.form.getRawValue();
    const title = value.title.trim();
    const body = value.body.trim();
    const existing = this.existing();
    this.sending.set(true);

    if (this.kind() === 'thread') {
      const school = Number(value.schoolId);
      const request$: Observable<ForumThread> = existing
        ? this.forum.updateThread(existing.id, { title, body })
        : this.forum.createThread({ title, body, ...(this.showSchool() && school > 0 ? { schoolId: school } : {}) });
      request$.subscribe({
        next: (thread) => this.done(!existing, () => this.savedThread.emit(thread)),
        error: (err: unknown) => this.fail(err),
      });
      return;
    }

    const request$: Observable<ForumReply> = existing
      ? this.forum.updateReply(existing.id, body)
      : this.forum.createReply(this.threadId() ?? 0, body);
    request$.subscribe({
      next: (reply) => this.done(!existing, () => this.savedReply.emit(reply)),
      error: (err: unknown) => this.fail(err),
    });
  }

  protected cancel(): void {
    this.cancelled.emit();
  }

  /** Wording of the general alert for the failure, as translation key and parameters. */
  protected readonly alert = computed(() => {
    const failure = this.failure();
    if (!failure || failure.kind === 'fields') return null;
    if (failure.kind === 'rateLimited') {
      return failure.seconds === null
        ? { key: 'forum.errors.rateLimitedNoDelay', params: {} }
        : { key: 'forum.errors.rateLimited', params: { seconds: failure.seconds } };
    }
    if (this.unverifiedRefusal()) {
      return { key: 'forum.errors.unverified', params: {} };
    }
    return { key: `forum.errors.${failure.kind}`, params: {} };
  });

  /**
   * A 403 for an account whose e-mail is unconfirmed is almost surely the verification rule (the page may not have known
   * the server sends mail): say so, and point to the profile where the link can be sent again.
   */
  protected readonly unverifiedRefusal = computed(
    () => this.failure()?.kind === 'forbidden' && this.tokens.user()?.emailVerified === false,
  );
  protected readonly lang = computed(() => this.locale.active());

  private done(reset: boolean, emit: () => void): void {
    this.sending.set(false);
    if (reset) {
      this.form.reset();
    }
    emit();
  }

  private fail(err: unknown): void {
    this.sending.set(false);
    const failure = classifyForumError(err);
    if (failure.kind === 'fields') {
      this.serverFields.set(failure.fields);
      this.focusFirstInvalid();
    }
    this.failure.set(failure);
    if (failure.kind === 'locked') this.locked.emit();
    if (failure.kind === 'rateLimited' && failure.seconds) this.startCooldown(failure.seconds);
  }

  private startCooldown(seconds: number): void {
    this.stopCooldown();
    this.cooldown.set(seconds);
    this.cooldownTimer = setInterval(() => {
      this.cooldown.update((n) => n - 1);
      if (this.cooldown() <= 0) this.stopCooldown();
    }, 1000);
  }

  private stopCooldown(): void {
    if (this.cooldownTimer !== null) clearInterval(this.cooldownTimer);
    this.cooldownTimer = null;
    this.cooldown.set(0);
  }

  private focusFirstInvalid(): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('[aria-invalid=true]')?.focus(), { injector: this.injector });
  }
}
