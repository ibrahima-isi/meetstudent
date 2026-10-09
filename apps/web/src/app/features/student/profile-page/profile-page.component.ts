import { Component, signal, effect, OnInit, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, User as UserIcon, Mail, Phone, MapPin, GraduationCap, Book, Save, Heart, Briefcase } from 'lucide-angular';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { WishlistService } from '@services/wishlist.service';
import { User } from '@models/entities';
import { ThemeToggleComponent } from '@shared/components/theme-toggle/theme-toggle.component';
import { UserDocumentsComponent } from '../user-documents/user-documents.component';
import { TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'app-profile-page',
  imports: [CommonModule, FormsModule, LucideAngularModule, UserDocumentsComponent, TranslocoDirective, ThemeToggleComponent],
  templateUrl: './profile-page.component.html'
})
export class ProfilePageComponent implements OnInit {
  private tokenService = inject(TokenService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  protected readonly wishlist = inject(WishlistService);

  readonly ArrowLeft = ArrowLeft;
  readonly UserIcon = UserIcon;
  readonly Mail = Mail;
  readonly Phone = Phone;
  readonly MapPin = MapPin;
  readonly GraduationCap = GraduationCap;
  readonly Book = Book;
  readonly Save = Save;
  readonly Heart = Heart;
  readonly Briefcase = Briefcase;

  profile = signal<Partial<User>>({
    firstname: '',
    lastname: '',
    email: '',
    role: { name: 'STUDENT' },
  });

  editedProfile = signal<Partial<User>>({ ...this.profile() });
  isEditing = signal(false);

  ngOnInit() {
    const currentUser = this.tokenService.user();
    if (currentUser) {
      this.profile.set(currentUser);
      this.editedProfile.set({ ...currentUser });
    }

    this.wishlist.load();
  }

  handleSave() {
    this.profile.set({ ...this.editedProfile() });
    // In a real app, you would send a PUT request to update user profile
    this.isEditing.set(false);
  }

  handleCancel() {
    this.editedProfile.set({ ...this.profile() });
    this.isEditing.set(false);
  }

  /** Navigations stay in the language the visitor is reading. */
  protected goTo(...segments: (string | number)[]): void {
    void this.router.navigate(['/', this.locale.active(), ...segments]);
  }
}
