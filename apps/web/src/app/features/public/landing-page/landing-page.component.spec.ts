import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { LandingPageComponent } from './landing-page.component';
import { SchoolService } from '@services/school.service';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('LandingPageComponent', () => {
  let component: LandingPageComponent;
  let fixture: ComponentFixture<LandingPageComponent>;
  let schoolServiceSpy: jasmine.SpyObj<SchoolService>;

  beforeEach(async () => {
    schoolServiceSpy = jasmine.createSpyObj('SchoolService', ['getSchools', 'schools']);
    schoolServiceSpy.getSchools.and.returnValue(of({ content: [] } as any));
    schoolServiceSpy.schools.and.returnValue([]);

    await TestBed.configureTestingModule({
      imports: [LandingPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SchoolService, useValue: schoolServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LandingPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should filter schools by search query', () => {
    const mockSchools = [
      { id: 1, name: 'Harvard', description: 'Ivy League', address: { city: 'Cambridge' }, type: 'Univ' },
      { id: 2, name: 'MIT', description: 'Tech school', address: { city: 'Cambridge' }, type: 'Univ' }
    ];
    component.schools.set(mockSchools as any);
    
    component.searchQuery.set('Harvard');
    fixture.detectChanges();
    
    expect(component.sortedSchools().length).toBe(1);
    expect(component.sortedSchools()[0].name).toBe('Harvard');
  });

  it('should filter schools by city', () => {
    const mockSchools = [
      { id: 1, name: 'Harvard', address: { city: 'Cambridge' }, type: 'Univ' },
      { id: 2, name: 'Stanford', address: { city: 'Stanford' }, type: 'Univ' }
    ];
    component.schools.set(mockSchools as any);
    
    component.selectedCity.set('Stanford');
    fixture.detectChanges();
    
    expect(component.sortedSchools().length).toBe(1);
    expect(component.sortedSchools()[0].name).toBe('Stanford');
  });
});
