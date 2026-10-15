import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AccreditationLinks } from './accreditation-links';

@Component({
  selector: 'app-course-accreditations',
  imports: [AccreditationLinks],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-accreditation-links kind="course" [resourceId]="courseId()" />`,
})
export class CourseAccreditations {
  readonly courseId = input.required<number>();
}
