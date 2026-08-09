import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { SchoolDetailPageComponent } from './school-detail-page.component';
import { ProgramService } from '@services/program.service';
import { CourseService } from '@services/course.service';
import { TokenService } from '@services/token.service';
import { of } from 'rxjs';
import { School, Program } from '@models/entities';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { RatingService } from '@services/rating.service';

describe('SchoolDetailPageComponent', () => {
  let component: SchoolDetailPageComponent;
  let fixture: ComponentFixture<SchoolDetailPageComponent>;
  let programServiceSpy: jasmine.SpyObj<ProgramService>;
  let courseServiceSpy: jasmine.SpyObj<CourseService>;
  let tokenServiceSpy: jasmine.SpyObj<TokenService>;
  let ratingServiceSpy: jasmine.SpyObj<RatingService>;

  const mockSchool: School = {
    id: 1,
    name: 'Test School',
    type: 'University',
    address: { location: 'Loc', city: 'City', country: 'Country' },
    description: 'Desc',
    tags: [{ id: 1, name: 'Tag1' }]
  };

  beforeEach(async () => {
    programServiceSpy = jasmine.createSpyObj('ProgramService', ['getPrograms']);
    courseServiceSpy = jasmine.createSpyObj('CourseService', ['getCoursesByProgram']);
    tokenServiceSpy = jasmine.createSpyObj('TokenService', ['isAuthenticated', 'user']);
    ratingServiceSpy = jasmine.createSpyObj('RatingService', ['rateSchool', 'rateProgram', 'rateCourse']);

    programServiceSpy.getPrograms.and.returnValue(of({ content: [] } as any));
    tokenServiceSpy.isAuthenticated.and.returnValue(true);

    await TestBed.configureTestingModule({
      imports: [SchoolDetailPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ProgramService, useValue: programServiceSpy },
        { provide: CourseService, useValue: courseServiceSpy },
        { provide: TokenService, useValue: tokenServiceSpy },
        { provide: RatingService, useValue: ratingServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SchoolDetailPageComponent);
    component = fixture.componentInstance;
    
    fixture.componentRef.setInput('school', mockSchool);
    fixture.componentRef.setInput('isAuthenticated', true);
    
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load programs on init', () => {
    const mockPrograms = [{ id: 1, name: 'Prog 1', school: { id: 1 } }];
    programServiceSpy.getPrograms.and.returnValue(of({ content: mockPrograms } as any));
    
    component.loadPrograms();
    
    expect(component.programs().length).toBe(1);
    expect(component.programs()[0].name).toBe('Prog 1');
  });

  it('should open courses modal', () => {
    const mockProgram: Program = { id: 1, name: 'Prog 1', duration: 3 };
    const mockCourses = [{ id: 1, name: 'Course 1' }];
    courseServiceSpy.getCoursesByProgram.and.returnValue(of(mockCourses as any));
    
    component.openCoursesModal(mockProgram);
    
    expect(component.showCoursesModal()).toBeTrue();
    expect(component.selectedProgram()).toEqual(mockProgram);
    expect(courseServiceSpy.getCoursesByProgram).toHaveBeenCalledWith(1);
  });

  it('should close courses modal', () => {
    component.showCoursesModal.set(true);
    component.closeCoursesModal();
    expect(component.showCoursesModal()).toBeFalse();
    expect(component.selectedProgram()).toBeNull();
  });
});
