import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TagsPage } from './tags-page';
import { API_URL } from '@services/api-config';

const api = 'http://api.test/api/v1';

describe('TagsPage', () => {
  let fixture: ComponentFixture<TagsPage>;
  let backend: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TagsPage);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('opens on the Tags tab and loads only the tags', () => {
    backend.expectOne(`${api}/tags`).flush([{ id: 1, name: 'PUBLIC' }]);
    fixture.detectChanges();
    expect(q('[role=tab][aria-selected=true]')?.textContent).toContain('Tags');
    expect(q('app-tags-section')).not.toBeNull();
    expect(q('app-accreditations-section')).toBeNull();
  });

  it('switches to the Accreditations tab', () => {
    backend.expectOne(`${api}/tags`).flush([]);
    q<HTMLButtonElement>('[data-tab=accreditations]')!.click();
    fixture.detectChanges();
    expect(q('[role=tab][aria-selected=true]')?.textContent).toContain('Accréditations');
    expect(q('app-tags-section')).toBeNull();
    backend
      .expectOne((r) => r.url === `${api}/accreditations`)
      .flush({ content: [], number: 0, totalPages: 0, totalElements: 0, size: 10 });
  });
});
