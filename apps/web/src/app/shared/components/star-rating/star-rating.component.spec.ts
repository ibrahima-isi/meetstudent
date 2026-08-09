import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { StarRatingComponent } from './star-rating.component';
import { RatingService } from '@services/rating.service';
import { TokenService } from '@services/token.service';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('StarRatingComponent', () => {
  let component: StarRatingComponent;
  let fixture: ComponentFixture<StarRatingComponent>;
  let ratingServiceSpy: jasmine.SpyObj<RatingService>;
  let tokenServiceSpy: jasmine.SpyObj<TokenService>;

  beforeEach(async () => {
    ratingServiceSpy = jasmine.createSpyObj('RatingService', ['rateSchool', 'rateProgram', 'rateCourse']);
    tokenServiceSpy = jasmine.createSpyObj('TokenService', ['user']);
    tokenServiceSpy.user.and.returnValue({ id: 1 } as any);

    await TestBed.configureTestingModule({
      imports: [StarRatingComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RatingService, useValue: ratingServiceSpy },
        { provide: TokenService, useValue: tokenServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(StarRatingComponent);
    component = fixture.componentInstance;
    
    // Set required inputs
    fixture.componentRef.setInput('itemId', 1);
    fixture.componentRef.setInput('itemType', 'school');
    
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should update rating on handleRate', () => {
    component.handleRate(4);
    expect(component.rating()).toBe(4);
  });

  it('should call rating service on submitRating for school', () => {
    ratingServiceSpy.rateSchool.and.returnValue(of({} as any));
    component.rating.set(5);
    component.comment.set('Great school!');
    
    component.submitRating();
    
    expect(ratingServiceSpy.rateSchool).toHaveBeenCalledWith(1, 1, 5, 'Great school!');
  });

  it('should show comment input when showCommentInput is true and rated', () => {
    fixture.componentRef.setInput('showCommentInput', true);
    component.rating.set(3);
    fixture.detectChanges();
    
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('textarea')).toBeTruthy();
  });

  it('should not show comment input when readonly is true', () => {
    fixture.componentRef.setInput('readonly', true);
    fixture.componentRef.setInput('showCommentInput', true);
    component.rating.set(3);
    fixture.detectChanges();
    
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('textarea')).toBeFalsy();
  });
});
