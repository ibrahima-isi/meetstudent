import { useState, useEffect } from 'react';
import { ArrowLeft, User, Mail, Phone, MapPin, GraduationCap, Book, Save, Heart, Briefcase } from 'lucide-react';

interface UserProfile {
  name: string;
  email: string;
  phone: string;
  city: string;
  userType: 'student' | 'teacher';
  // Student specific
  bacType?: string;
  studyLevel?: string;
  // Teacher specific
  specialty?: string;
}

interface ProfilePageProps {
  onBack: () => void;
}

export function ProfilePage({ onBack }: ProfilePageProps) {
  const [profile, setProfile] = useState<UserProfile>(() => {
    const saved = localStorage.getItem('userProfile');
    return saved ? JSON.parse(saved) : {
      name: '',
      email: '',
      phone: '',
      city: '',
      userType: 'student',
      bacType: '',
      studyLevel: '',
      specialty: '',
    };
  });

  const [wishlist, setWishlist] = useState<number[]>(() => {
    const saved = localStorage.getItem('wishlist');
    return saved ? JSON.parse(saved) : [];
  });

  const [isEditing, setIsEditing] = useState(false);
  const [editedProfile, setEditedProfile] = useState(profile);

  useEffect(() => {
    // Load profile from localStorage on mount
    const savedProfile = localStorage.getItem('userProfile');
    if (savedProfile) {
      const parsed = JSON.parse(savedProfile);
      setProfile(parsed);
      setEditedProfile(parsed);
    }
  }, []);

  const handleSave = () => {
    setProfile(editedProfile);
    localStorage.setItem('userProfile', JSON.stringify(editedProfile));
    setIsEditing(false);
  };

  const handleCancel = () => {
    setEditedProfile(profile);
    setIsEditing(false);
  };

  const removeFromWishlist = (programmeId: number) => {
    const newWishlist = wishlist.filter(id => id !== programmeId);
    setWishlist(newWishlist);
    localStorage.setItem('wishlist', JSON.stringify(newWishlist));
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200">
        <div className="max-w-4xl mx-auto px-4 py-6">
          <button
            onClick={onBack}
            className="flex items-center gap-2 text-gray-700 hover:text-gray-900 mb-4"
          >
            <ArrowLeft className="w-5 h-5" />
            <span>Retour</span>
          </button>
          <h1 className="text-gray-900">Mon Profil</h1>
        </div>
      </div>

      {/* Content */}
      <div className="max-w-4xl mx-auto px-4 py-8">
        {/* Profile Information */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-gray-900">Informations personnelles</h2>
            {!isEditing ? (
              <button
                onClick={() => setIsEditing(true)}
                className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
              >
                Modifier
              </button>
            ) : (
              <div className="flex gap-2">
                <button
                  onClick={handleCancel}
                  className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  Annuler
                </button>
                <button
                  onClick={handleSave}
                  className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
                >
                  <Save className="w-4 h-4" />
                  <span>Enregistrer</span>
                </button>
              </div>
            )}
          </div>

          <div className="space-y-4">
            {/* Name */}
            <div>
              <label className="block text-gray-700 mb-2">Nom complet</label>
              {isEditing ? (
                <div className="relative">
                  <User className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                  <input
                    type="text"
                    value={editedProfile.name}
                    onChange={(e) => setEditedProfile({ ...editedProfile, name: e.target.value })}
                    className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                    placeholder="Votre nom complet"
                  />
                </div>
              ) : (
                <div className="flex items-center gap-3 text-gray-900">
                  <User className="w-5 h-5 text-gray-400" />
                  <span>{profile.name || 'Non renseigné'}</span>
                </div>
              )}
            </div>

            {/* Email */}
            <div>
              <label className="block text-gray-700 mb-2">Email</label>
              {isEditing ? (
                <div className="relative">
                  <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                  <input
                    type="email"
                    value={editedProfile.email}
                    onChange={(e) => setEditedProfile({ ...editedProfile, email: e.target.value })}
                    className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                    placeholder="votre@email.com"
                  />
                </div>
              ) : (
                <div className="flex items-center gap-3 text-gray-900">
                  <Mail className="w-5 h-5 text-gray-400" />
                  <span>{profile.email || 'Non renseigné'}</span>
                </div>
              )}
            </div>

            {/* Phone */}
            <div>
              <label className="block text-gray-700 mb-2">Téléphone</label>
              {isEditing ? (
                <div className="relative">
                  <Phone className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                  <input
                    type="tel"
                    value={editedProfile.phone}
                    onChange={(e) => setEditedProfile({ ...editedProfile, phone: e.target.value })}
                    className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                    placeholder="+221 XX XXX XX XX"
                  />
                </div>
              ) : (
                <div className="flex items-center gap-3 text-gray-900">
                  <Phone className="w-5 h-5 text-gray-400" />
                  <span>{profile.phone || 'Non renseigné'}</span>
                </div>
              )}
            </div>

            {/* City */}
            <div>
              <label className="block text-gray-700 mb-2">Ville</label>
              {isEditing ? (
                <div className="relative">
                  <MapPin className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                  <input
                    type="text"
                    value={editedProfile.city}
                    onChange={(e) => setEditedProfile({ ...editedProfile, city: e.target.value })}
                    className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                    placeholder="Dakar, Saint-Louis, etc."
                  />
                </div>
              ) : (
                <div className="flex items-center gap-3 text-gray-900">
                  <MapPin className="w-5 h-5 text-gray-400" />
                  <span>{profile.city || 'Non renseigné'}</span>
                </div>
              )}
            </div>

            {/* User Type */}
            <div>
              <label className="block text-gray-700 mb-2">Type de profil</label>
              <div className="flex items-center gap-3 text-gray-900">
                {profile.userType === 'student' ? (
                  <>
                    <GraduationCap className="w-5 h-5 text-gray-400" />
                    <span>Étudiant</span>
                  </>
                ) : (
                  <>
                    <Briefcase className="w-5 h-5 text-gray-400" />
                    <span>Enseignant</span>
                  </>
                )}
              </div>
            </div>

            {/* Student Specific Fields */}
            {profile.userType === 'student' && (
              <>
                <div>
                  <label className="block text-gray-700 mb-2">Type de BAC</label>
                  {isEditing ? (
                    <div className="relative">
                      <GraduationCap className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                      <select
                        value={editedProfile.bacType}
                        onChange={(e) => setEditedProfile({ ...editedProfile, bacType: e.target.value })}
                        className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent appearance-none bg-white"
                      >
                        <option value="">Sélectionnez votre BAC</option>
                        <option value="bac-general">Bac Général</option>
                        <option value="bac-techno-sti2d">Bac Technologique - STI2D</option>
                        <option value="bac-techno-stmg">Bac Technologique - STMG</option>
                        <option value="bac-pro">Bac Professionnel</option>
                      </select>
                    </div>
                  ) : (
                    <div className="flex items-center gap-3 text-gray-900">
                      <GraduationCap className="w-5 h-5 text-gray-400" />
                      <span>{profile.bacType || 'Non renseigné'}</span>
                    </div>
                  )}
                </div>

                <div>
                  <label className="block text-gray-700 mb-2">Niveau d'études</label>
                  {isEditing ? (
                    <div className="relative">
                      <Book className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                      <select
                        value={editedProfile.studyLevel}
                        onChange={(e) => setEditedProfile({ ...editedProfile, studyLevel: e.target.value })}
                        className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent appearance-none bg-white"
                      >
                        <option value="">Sélectionnez votre niveau</option>
                        <option value="l1">Licence 1 (L1)</option>
                        <option value="l2">Licence 2 (L2)</option>
                        <option value="l3">Licence 3 (L3)</option>
                        <option value="m1">Master 1 (M1)</option>
                        <option value="m2">Master 2 (M2)</option>
                      </select>
                    </div>
                  ) : (
                    <div className="flex items-center gap-3 text-gray-900">
                      <Book className="w-5 h-5 text-gray-400" />
                      <span>{profile.studyLevel || 'Non renseigné'}</span>
                    </div>
                  )}
                </div>
              </>
            )}

            {/* Teacher Specific Fields */}
            {profile.userType === 'teacher' && (
              <div>
                <label className="block text-gray-700 mb-2">Spécialité</label>
                {isEditing ? (
                  <div className="relative">
                    <Briefcase className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                    <input
                      type="text"
                      value={editedProfile.specialty}
                      onChange={(e) => setEditedProfile({ ...editedProfile, specialty: e.target.value })}
                      className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                      placeholder="Mathématiques, Physique, etc."
                    />
                  </div>
                ) : (
                  <div className="flex items-center gap-3 text-gray-900">
                    <Briefcase className="w-5 h-5 text-gray-400" />
                    <span>{profile.specialty || 'Non renseigné'}</span>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Wishlist */}
        {profile.userType === 'student' && (
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <h2 className="text-gray-900 mb-4">Ma liste de souhaits</h2>
            {wishlist.length > 0 ? (
              <div className="space-y-3">
                {wishlist.map(id => (
                  <div
                    key={id}
                    className="flex items-center justify-between p-4 border border-gray-200 rounded-lg"
                  >
                    <div className="flex items-center gap-3">
                      <Heart className="w-5 h-5 text-red-600 fill-current" />
                      <span className="text-gray-900">Programme #{id}</span>
                    </div>
                    <button
                      onClick={() => removeFromWishlist(id)}
                      className="px-3 py-1 text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                    >
                      Retirer
                    </button>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-8 text-gray-500">
                <Heart className="w-12 h-12 mx-auto mb-3 text-gray-300" />
                <p>Aucune formation dans votre liste de souhaits</p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
