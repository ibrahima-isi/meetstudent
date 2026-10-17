import { ChangeDetectionStrategy, Component, OnDestroy, computed, input, output, signal } from '@angular/core';
import { validateImage } from '@services/media.service';

/** A single image picker: validates like the API, previews locally, emits the chosen file. */
@Component({
  selector: 'app-image-field',
  host: { class: 'block' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-2">
      <label [for]="fieldId()" class="text-sm font-medium text-foreground">{{ label() }}</label>
      @if (shown(); as src) {
        <img [src]="src" data-preview alt="" class="h-28 w-full rounded-md border border-border bg-muted object-contain" />
      } @else if (existing()) {
        <p class="text-xs text-muted-foreground">Une image est déjà enregistrée. Choisissez un fichier pour la remplacer.</p>
      }
      <input
        [id]="fieldId()"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        (change)="onFile($event)"
        class="block w-full text-sm text-muted-foreground file:mr-3 file:rounded-md file:border file:border-border-strong file:bg-card file:px-3 file:py-1.5 file:text-sm file:font-medium hover:file:bg-accent"
      />
      <p class="text-xs text-muted-foreground">JPEG, PNG ou WebP, 10 Mo maximum.</p>
      @if (error()) {
        <p class="text-xs text-destructive" data-error>{{ error() }}</p>
      }
    </div>
  `,
})
export class ImageField implements OnDestroy {
  readonly fieldId = input.required<string>();
  readonly label = input.required<string>();
  /** Browser-loadable url of the image already stored, if known. */
  readonly current = input<string | null>(null);
  /** An image is stored although its url is not known. */
  readonly existing = input(false);
  /** The chosen file, or null when it was cleared or rejected. */
  readonly fileChange = output<File | null>();

  readonly error = signal('');
  private readonly previewUrl = signal<string | null>(null);
  readonly shown = computed(() => this.previewUrl() ?? this.current());

  ngOnDestroy() {
    this.revoke();
  }

  onFile(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.revoke();
    this.error.set('');
    if (!file) {
      this.fileChange.emit(null);
      return;
    }
    const error = validateImage(file);
    if (error) {
      input.value = '';
      this.error.set(error);
      this.fileChange.emit(null);
      return;
    }
    this.previewUrl.set(URL.createObjectURL(file));
    this.fileChange.emit(file);
  }

  private revoke() {
    const url = this.previewUrl();
    if (url) URL.revokeObjectURL(url);
    this.previewUrl.set(null);
  }
}
