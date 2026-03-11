import { Component, signal, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LandingPageComponent } from './features/public/landing-page/landing-page.component';
import { HomePageComponent } from './features/student/home-page/home-page.component';
import { SchoolDetailPageComponent } from './features/student/school-detail-page/school-detail-page.component';
import { ProfilePageComponent } from './features/student/profile-page/profile-page.component';
import { LoginFormComponent } from './features/auth/login-form/login-form.component';
import { RegisterFormComponent } from './features/auth/register-form/register-form.component';
import { EmailVerificationComponent } from './features/auth/email-verification/email-verification.component';
import { School, User } from '@models/entities';
import { TokenService } from '@services/token.service';

type ViewState = 'landing' | 'login' | 'register' | 'verify' | 'home' | 'school-detail' | 'profile';

@Component({
  selector: 'app-root',
  imports: [
    CommonModule,
    LandingPageComponent,
    HomePageComponent,
    SchoolDetailPageComponent,
    ProfilePageComponent,
    LoginFormComponent,
    RegisterFormComponent,
    EmailVerificationComponent
  ],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private tokenService = inject(TokenService);

  view = signal<ViewState>(this.tokenService.isAuthenticated() ? 'home' : 'landing');
  userEmail = signal('');
  selectedSchool = signal<School | null>(null);
  
  isAuthenticated = computed(() => this.tokenService.isAuthenticated());
  currentUser = computed(() => this.tokenService.user());

  handleRegisterSuccess(email: string) {
    this.userEmail.set(email);
    this.view.set('verify');
  }

  handleVerificationSuccess() {
    this.view.set('login');
  }

  handleLoginSuccess() {
    this.view.set('home');
  }

  handleLogout() {
    this.tokenService.clear();
    this.view.set('landing');
    this.userEmail.set('');
    this.selectedSchool.set(null);
  }

  handleSchoolClick(school: School) {
    this.selectedSchool.set(school);
    this.view.set('school-detail');
  }

  handleBackToHome() {
    this.view.set(this.isAuthenticated() ? 'home' : 'landing');
    this.selectedSchool.set(null);
  }

  handleProfileClick() {
    if (this.isAuthenticated()) {
      this.view.set('profile');
    } else {
      this.view.set('login');
    }
  }
}
