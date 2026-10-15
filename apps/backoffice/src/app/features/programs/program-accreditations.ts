import { Component, input } from '@angular/core';
import { AccreditationLinks } from './accreditation-links';

@Component({
  selector: 'app-program-accreditations',
  imports: [AccreditationLinks],
  template: `<app-accreditation-links kind="program" [resourceId]="programId()" />`,
})
export class ProgramAccreditations {
  readonly programId = input.required<number>();
}
