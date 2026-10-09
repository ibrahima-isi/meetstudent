import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="flex min-h-screen flex-col items-center justify-center gap-3 bg-slate-100 px-4">
      <h1 class="text-2xl font-semibold text-slate-900">Page introuvable</h1>
      <p class="text-sm text-slate-500">Cette adresse n'existe pas.</p>
      <a routerLink="/" class="text-sm font-medium text-indigo-600 hover:underline">Retour à l'accueil</a>
    </main>
  `,
})
export class NotFoundPage {}
