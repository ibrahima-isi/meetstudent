import { Component, input, signal } from '@angular/core';

const ERROR_IMG_SRC = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODgiIGhlaWdodD0iODgiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgc3Ryb2tlPSIjMDAwIiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBvcGFjaXR5PSIuMyIgZmlsbD0ibm9uZSIgc3Ryb2tlLXdpZHRoPSIzLjciPjxyZWN0IHg9IjE2IiB5PSIxNiIgd2lkdGg9IjU2IiBoZWlnaHQ9IjU2IiByeD0iNiIvPjxwYXRoIGQ9Im0xNiA1OCAxNi0xOCAzMiAzMiIvPjxjaXJjbGUgY3g9IjUzIiBjeT0iMzUiIHI9IjciLz48L3N2Zz4KCg==';

@Component({
  selector: 'app-image-with-fallback',
  template: `
    @if (didError()) {
      <div [class]="'inline-block bg-gray-100 text-center align-middle ' + customClass()">
        <div class="flex items-center justify-center w-full h-full">
          <img [src]="errorSrc" alt="Error loading image" [attr.data-original-url]="src()" />
        </div>
      </div>
    } @else {
      <img [src]="src()" [alt]="alt()" [class]="customClass()" (error)="handleError()" />
    }
  `
})
export class ImageWithFallbackComponent {
  src = input.required<string>();
  alt = input<string>('');
  customClass = input<string>('', { alias: 'class' });

  didError = signal(false);
  errorSrc = ERROR_IMG_SRC;

  handleError() {
    this.didError.set(true);
  }
}
