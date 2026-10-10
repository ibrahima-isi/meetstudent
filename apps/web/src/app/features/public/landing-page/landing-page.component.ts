import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CtaBandComponent } from './sections/cta-band.component';
import { FeaturedSchoolsComponent } from './sections/featured-schools.component';
import { HowItWorksComponent } from './sections/how-it-works.component';
import { KeyFiguresComponent } from './sections/key-figures.component';
import { LandingHeroComponent } from './sections/landing-hero.component';
import { SiteFooterComponent } from './sections/site-footer.component';
import { TestimonialsComponent } from './sections/testimonials.component';

/** The marketing home of `/:lang`. The school catalogue lives at `/:lang/schools`. */
@Component({
  selector: 'app-landing-page',
  imports: [
    LandingHeroComponent,
    KeyFiguresComponent,
    HowItWorksComponent,
    FeaturedSchoolsComponent,
    TestimonialsComponent,
    CtaBandComponent,
    SiteFooterComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-landing-hero />
    <app-key-figures />
    <app-how-it-works />
    <app-featured-schools />
    <app-testimonials />
    <app-cta-band />
    <app-site-footer />
  `,
})
export class LandingPageComponent {}
