import { Component, signal, effect, OnInit, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, User as UserIcon, Mail, Phone, MapPin, GraduationCap, Book, Save, Heart, Briefcase } from 'lucide-angular';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { User } from '@models/entities';
import { UserDocumentsComponent } from '../user-documents/user-documents.component';

@Component({
  selector: 'app-profile-page',
  imports: [CommonModule, FormsModule, LucideAngularModule, UserDocumentsComponent],
  templateUrl: './profile-page.component.html'
})
export class ProfilePageComponent implements OnInit {
  private tokenService = inject(TokenService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

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
  wishlist = signal<number[]>([]);

  ngOnInit() {
    const currentUser = this.tokenService.user();
    if (currentUser) {
      this.profile.set(currentUser);
      this.editedProfile.set({ ...currentUser });
    }

    if (typeof localStorage !== 'undefined') {
      const savedWishlist = localStorage.getItem('wishlist');
      if (savedWishlist) {
        this.wishlist.set(JSON.parse(savedWishlist));
      }
    }
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

  removeFromWishlist(programmeId: number) {
    const newWishlist = this.wishlist().filter(id => id !== programmeId);
    this.wishlist.set(newWishlist);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('wishlist', JSON.stringify(newWishlist));
      window.dispatchEvent(new Event('wishlistUpdated'));
    }
  }
}
