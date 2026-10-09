import { Component, input, output, signal, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Star, MessageSquare } from 'lucide-angular';
import { RatingService } from '@services/rating.service';
import { TokenService } from '@services/token.service';
import { Observable } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import { roundRating } from '@shared/format-rating';

@Component({
  selector: 'app-star-rating',
  imports: [CommonModule, FormsModule, LucideAngularModule, TranslocoPipe],
  template: `
    <!--
      The pipe, not *transloco: a structural directive would hide the stars
      until the bundle loads, and this widget sits inside other pages.
    -->
    <div class="flex flex-col gap-3">
      <div class="flex items-center gap-1">
        @for (value of [1, 2, 3, 4, 5]; track value) {
          <button
            type="button"
            (click)="handleRate(value)"
            (mouseenter)="handleMouseEnter(value)"
            (mouseleave)="handleMouseLeave()"
            [disabled]="locked()"
            class="transition-transform outline-none"
            [class]="locked() ? 'cursor-default' : 'cursor-pointer hover:scale-110 focus:scale-110'"
          >
            <lucide-icon
              [img]="Star"
              class="w-6 h-6"
              [class]="value <= displayRating() ? 'fill-yellow-400 text-yellow-400' : 'fill-gray-200 text-gray-200 dark:fill-gray-700 dark:text-gray-700'"
            ></lucide-icon>
          </button>
        }
        @if (rating() > 0 && showValue()) {
          <span class="ml-2 text-sm font-medium text-muted-foreground">{{ shownRating() }}/5</span>
        }
      </div>

      @if (!readonly() && !submitted() && showCommentInput() && rating() > 0) {
        <div class="flex flex-col gap-2 animate-in fade-in slide-in-from-top-1">
          <textarea
            [(ngModel)]="comment"
            [placeholder]="'rating.commentPlaceholder' | transloco"
            class="w-full p-3 text-sm border border-border rounded-lg bg-card text-foreground placeholder:text-muted-foreground focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none resize-none"
            rows="3"
          ></textarea>
          <button
            (click)="submitRating()"
            [disabled]="isSubmitting()"
            class="self-end px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {{ (isSubmitting() ? 'rating.submitting' : 'rating.submit') | transloco }}
          </button>
        </div>
      }

      @if (submitted()) {
        <p data-testid="rating-done" role="status" class="text-sm font-medium text-green-700 dark:text-green-400">{{ 'rating.thanks' | transloco }}</p>
      }

      @if (failed()) {
        <p data-testid="rating-error" role="alert" class="text-sm font-medium text-red-700 dark:text-red-400">{{ 'rating.error' | transloco }}</p>
      }
    </div>
  `
})
export class StarRatingComponent {
  itemId = input.required<number>();
  itemType = input.required<'school' | 'program' | 'course'>();
  initialRating = input<number>(0);
  readonly = input<boolean>(false);
  showValue = input<boolean>(true);
  showCommentInput = input<boolean>(true);
  
  onRate = output<{note: number, comment?: string}>();

  private ratingService = inject(RatingService);
  private tokenService = inject(TokenService);

  rating = signal<number>(0);
  hoverRating = signal<number>(0);
  comment = signal<string>('');
  isSubmitting = signal(false);
  /** True once the API accepted the rating: the widget then stays read-only. */
  submitted = signal(false);
  /** True after a failed request, until the next attempt. */
  failed = signal(false);
  /** The average as read by people; `rating` itself stays exact for the stars. */
  protected shownRating = computed(() => roundRating(this.rating()));
  protected locked = computed(() => this.readonly() || this.submitted());

  readonly Star = Star;
  readonly MessageSquare = MessageSquare;

  constructor() {
    // Initialize rating from input
    const ratingSub = signal(0);
    this.rating.set(this.initialRating() || 0);
  }

  handleRate(value: number) {
    if (this.locked()) return;
    this.rating.set(value);
    if (!this.showCommentInput()) {
      this.submitRating();
    }
  }

  submitRating() {
    const note = this.rating();
    if (note === 0 || this.isSubmitting() || this.submitted()) return;

    const user = this.tokenService.user();
    if (!user || !user.id) {
      this.onRate.emit({ note, comment: this.comment() });
      return;
    }

    this.failed.set(false);
    this.isSubmitting.set(true);
    const userId = user.id;
    const commentText = this.comment();

    const obs: Observable<any> = this.itemType() === 'school' 
      ? this.ratingService.rateSchool(this.itemId(), userId, note, commentText)
      : this.itemType() === 'program'
      ? this.ratingService.rateProgram(this.itemId(), userId, note, commentText)
      : this.ratingService.rateCourse(this.itemId(), userId, note, commentText);

    obs.subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.submitted.set(true);
        this.onRate.emit({ note, comment: commentText });
      },
      error: () => {
        // Not reported to the parent: the rating was not saved.
        this.isSubmitting.set(false);
        this.failed.set(true);
      }
    });
  }

  handleMouseEnter(value: number) {
    if (!this.locked()) {
      this.hoverRating.set(value);
    }
  }

  handleMouseLeave() {
    if (!this.locked()) {
      this.hoverRating.set(0);
    }
  }

  displayRating() {
    return this.hoverRating() || this.rating();
  }
}
