import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { AlertCircle, CheckCircle, Loader2, LucideAngularModule, Mail, PenLine, Send } from 'lucide-angular';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { MeshBackgroundComponent } from '@shared/components/mesh-background/mesh-background.component';
import { SocialLinksComponent } from '@shared/components/social-links/social-links.component';
import { RevealDirective } from '@shared/directives/reveal.directive';
import { ContactService } from '@services/contact.service';
import { LocaleService } from '@services/locale.service';
import { SiteSettings, SiteSettingsService } from '@services/site-settings.service';

const MAX_SCHOOL_LENGTH = 100;

/** The server limits, mirrored so a visitor is told before sending rather than after. */
const MAX_LENGTH = { email: 254, subject: 150, message: 5000 } as const;

type Field = keyof typeof MAX_LENGTH;
const FIELDS = Object.keys(MAX_LENGTH) as Field[];

/** Every control character but tab, CR and LF: the server rejects them. */
const CONTROL_CHARS = /[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/;
const EMAIL_SHAPE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const notBlank = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim() === '' ? { required: true } : null;

const noControlChars = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && CONTROL_CHARS.test(control.value) ? { invalid: true } : null;

const emailShape = (control: AbstractControl): ValidationErrors | null => {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  return value === '' || EMAIL_SHAPE.test(value) ? null : { email: true };
};

/** `{field: message}` on a 400; anything else is not a validation answer. */
function fieldErrorsOf(body: unknown): Field[] {
  if (typeof body !== 'object' || body === null) return [];
  return FIELDS.filter((field) => field in body);
}

interface FieldError {
  key: string;
  max?: number;
}

/**
 * The Contact page: how to reach MeetStudent. A message form (sent by e-mail and
 * kept in the back office), the address and social links an admin set in the site
 * settings. The form never waits for those settings: they only fill the sides.
 */
@Component({
  selector: 'app-contact-page',
  imports: [
    ReactiveFormsModule,
    TranslocoDirective,
    LucideAngularModule,
    ErrorStateComponent,
    MeshBackgroundComponent,
    SocialLinksComponent,
    RevealDirective,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './contact-page.component.html',
})
export class ContactPageComponent {
  private readonly settingsService = inject(SiteSettingsService);
  private readonly contact = inject(ContactService);
  private readonly locale = inject(LocaleService);
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly Mail = Mail;
  protected readonly PenLine = PenLine;
  protected readonly Send = Send;
  protected readonly Loader2 = Loader2;
  protected readonly CheckCircle = CheckCircle;
  protected readonly AlertCircle = AlertCircle;
  protected readonly max = MAX_LENGTH;

  private readonly lang = toSignal(this.transloco.langChanges$);
  private readonly sentNotice = viewChild<ElementRef<HTMLElement>>('sentNotice');

  protected readonly status = signal<'loading' | 'loaded' | 'error'>('loading');
  protected readonly email = signal('');
  protected readonly settings = signal<SiteSettings | null>(null);

  protected readonly sending = signal(false);
  protected readonly sent = signal(false);
  /** Which general alert is showing: `rateLimited` or `failed` (a translation key suffix). */
  protected readonly alert = signal<'' | 'rateLimited' | 'failed'>('');
  /** Fields the server refused; each clears as soon as the visitor edits it. */
  private readonly serverErrors = signal<Partial<Record<Field, true>>>({});

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, notBlank, emailShape, Validators.maxLength(MAX_LENGTH.email), noControlChars]],
    subject: ['', [Validators.required, notBlank, Validators.maxLength(MAX_LENGTH.subject), noControlChars]],
    message: ['', [Validators.required, notBlank, Validators.maxLength(MAX_LENGTH.message), noControlChars]],
    /** Honeypot: invisible to people, tempting to bots. */
    website: [''],
  });

  protected readonly messageLength = toSignal(this.form.controls.message.valueChanges, { initialValue: '' });
  protected readonly counter = computed(() => this.messageLength().length);

  /** From `?school=`; unknown because a repeated parameter arrives as an array. */
  readonly school = input<unknown>();

  /** The school name made safe for a subject line; empty when absent or not a string. */
  private readonly schoolName = computed(() => {
    const raw = this.school();
    return typeof raw === 'string' ? raw.replace(/[\x00-\x1F\x7F]/g, ' ').trim().slice(0, MAX_SCHOOL_LENGTH).trim() : '';
  });

  /** A bare mail link, plus a subject only when a usable school was named. */
  protected readonly mailto = computed(() => {
    const base = 'mailto:' + this.email();
    const school = this.schoolName();
    if (!school) return base;
    this.lang();
    return `${base}?subject=${encodeURIComponent(this.transloco.translate('contact.applySubject', { school }))}`;
  });

  constructor() {
    this.load();

    for (const field of FIELDS) {
      this.form.controls[field].valueChanges.subscribe(() => {
        if (this.serverErrors()[field]) {
          this.serverErrors.update(({ [field]: _cleared, ...rest }) => rest);
        }
      });
    }

    // Pre-fill the subject, unless the visitor already wrote their own.
    effect(() => {
      const school = this.schoolName();
      this.lang();
      if (!school) return;
      untracked(() => {
        const subject = this.form.controls.subject;
        if (!subject.dirty) {
          subject.setValue(this.transloco.translate('contact.applySubject', { school }));
        }
      });
    });
  }

  protected load(): void {
    this.status.set('loading');
    this.settingsService.get().subscribe({
      next: (settings) => {
        this.settings.set(settings);
        this.email.set(settings.contactEmail);
        this.status.set('loaded');
      },
      error: () => this.status.set('error'),
    });
  }

  /** The message to show under a field, or null while it is fine or not yet visited. */
  protected errorFor(field: Field): FieldError | null {
    if (this.serverErrors()[field]) return { key: 'invalid' };
    const control = this.form.controls[field];
    if (!control.touched || control.valid) return null;
    if (control.hasError('required')) return { key: 'required' };
    if (control.hasError('email')) return { key: 'emailInvalid' };
    if (control.hasError('maxlength')) return { key: 'tooLong', max: MAX_LENGTH[field] };
    return { key: 'invalid' };
  }

  protected describedBy(field: Field): string | null {
    const ids = [this.errorFor(field) ? `contact-${field}-error` : '', field === 'message' ? 'contact-message-count' : ''];
    return ids.filter(Boolean).join(' ') || null;
  }

  protected submit(): void {
    if (this.sending()) return;

    this.sent.set(false);
    this.alert.set('');
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.focusFirstInvalid();
      return;
    }

    const value = this.form.getRawValue();
    this.sending.set(true);
    this.contact
      .send({
        email: value.email.trim(),
        subject: value.subject.trim(),
        message: value.message.trim(),
        language: this.locale.active(),
        website: value.website,
      })
      .subscribe({
        next: () => {
          this.sending.set(false);
          this.form.reset();
          this.sent.set(true);
          afterNextRender(() => this.sentNotice()?.nativeElement.focus(), { injector: this.injector });
        },
        error: (err: unknown) => {
          this.sending.set(false);
          this.fail(err);
        },
      });
  }

  private fail(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      const refused = err.status === 400 ? fieldErrorsOf(err.error) : [];
      if (refused.length > 0) {
        this.serverErrors.set(Object.fromEntries(refused.map((field) => [field, true])));
        this.focusFirstInvalid();
        return;
      }
      if (err.status === 429) {
        this.alert.set('rateLimited');
        return;
      }
    }
    this.alert.set('failed');
  }

  private focusFirstInvalid(): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('form [aria-invalid=true]')?.focus(), {
      injector: this.injector,
    });
  }
}
