import { Component, output, signal, effect, OnInit, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, ArrowLeft, User as UserIcon, Mail, Phone, MapPin, GraduationCap, Book, Save, Heart, Briefcase } from 'lucide-angular';
import { TokenService } from '@services/token.service';
import { User } from '@models/entities';

@Component({
  selector: 'app-profile-page',
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './profile-page.component.html'
})
export class ProfilePageComponent implements OnInit {
  onBack = output<void>();

  private tokenService = inject(TokenService);

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

  removeFromWishlist(programmeId: number) {
    const newWishlist = this.wishlist().filter(id => id !== programmeId);
    this.wishlist.set(newWishlist);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('wishlist', JSON.stringify(newWishlist));
      window.dispatchEvent(new Event('wishlistUpdated'));
    }
  }
}
