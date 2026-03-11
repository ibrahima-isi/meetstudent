import { useState, useEffect } from 'react';
import { ArrowLeft, MapPin, Star, Heart, GraduationCap, Clock, Calendar, Users, ArrowUpDown } from 'lucide-react';
import { ImageWithFallback } from './figma/ImageWithFallback';
import { PROGRAMMES } from '../data/programmes';
import { StarRating } from './StarRating';

interface Programme {
  id: number;
  name: string;
  duration: string;
  level: string;
  rating: number;
  reviewCount: number;
  description: string;
  startDate: string;
  capacity: number;
  enrolled: number;
}

interface School {
  id: number;
  name: string;
  type: string;
  address: string;
  city: string;
  rating: number;
  reviewCount: number;
  image: string;
  description: string;
}

interface SchoolDetailPageProps {
  school: School;
  onBack: () => void;
  isAuthenticated?: boolean;
  onLoginPrompt?: () => void;
}

export function SchoolDetailPage({ school, onBack, isAuthenticated = false, onLoginPrompt }: SchoolDetailPageProps) {
  const [wishlist, setWishlist] = useState<number[]>(() => {
    const saved = localStorage.getItem('wishlist');
    return saved ? JSON.parse(saved) : [];
  });
  const [showLoginPrompt, setShowLoginPrompt] = useState(false);
  const [sortBy, setSortBy] = useState<'name' | 'places'>('name');

  const programmes = PROGRAMMES[school.id] || [];

  // Sort programmes
  const sortedProgrammes = [...programmes].sort((a, b) => {
    if (sortBy === 'name') {
      return a.name.localeCompare(b.name, 'fr');
    } else if (sortBy === 'places') {
      const aPlaces = a.capacity - a.enrolled;
      const bPlaces = b.capacity - b.enrolled;
      return bPlaces - aPlaces; // Descending order
    }
    return 0;
  });

  // Listen for wishlist updates
  useEffect(() => {
    const updateWishlist = () => {
      const saved = localStorage.getItem('wishlist');
      setWishlist(saved ? JSON.parse(saved) : []);
    };

    window.addEventListener('wishlistUpdated', updateWishlist);
    
    return () => {
      window.removeEventListener('wishlistUpdated', updateWishlist);
    };
  }, []);

  const toggleWishlist = (programmeId: number) => {
    if (!isAuthenticated) {
      setShowLoginPrompt(true);
      return;
    }

    const newWishlist = wishlist.includes(programmeId)
      ? wishlist.filter(id => id !== programmeId)
      : [...wishlist, programmeId];
    
    setWishlist(newWishlist);
    localStorage.setItem('wishlist', JSON.stringify(newWishlist));
    
    // Dispatch custom event to update cart
    window.dispatchEvent(new Event('wishlistUpdated'));
  };

  const handleLoginClick = () => {
    setShowLoginPrompt(false);
    if (onLoginPrompt) {
      onLoginPrompt();
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Login Prompt Modal */}
      {showLoginPrompt && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-6">
            <h3 className="text-gray-900 mb-2">Connexion requise</h3>
            <p className="text-gray-600 mb-6">
              Vous devez être connecté pour ajouter des formations à votre liste de souhaits.
            </p>
            <div className="flex gap-3">
              <button
                onClick={handleLoginClick}
                className="flex-1 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
              >
                Se connecter
              </button>
              <button
                onClick={() => setShowLoginPrompt(false)}
                className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Annuler
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Header Image */}
      <div className="relative h-64 md:h-80">
        <ImageWithFallback
          src={school.image}
          alt={school.name}
          className="w-full h-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" />
        <button
          onClick={onBack}
          className="absolute top-4 left-4 flex items-center gap-2 px-4 py-2 bg-white rounded-lg shadow-md hover:bg-gray-50 transition-colors"
        >
          <ArrowLeft className="w-5 h-5" />
          <span>Retour</span>
        </button>
        <div className="absolute bottom-6 left-4 right-4 text-white">
          <div className="max-w-7xl mx-auto px-4">
            <span className="inline-block px-3 py-1 bg-white/20 backdrop-blur-sm rounded-full mb-2">
              {school.type}
            </span>
            <h1 className="text-white mb-3" style={{ fontSize: '2rem', fontWeight: 700 }}>{school.name}</h1>
            <div className="flex items-center gap-4 flex-wrap">
              <div className="flex items-center gap-2">
                <MapPin className="w-5 h-5" />
                <span>{school.address}, {school.city}</span>
              </div>
              {isAuthenticated && (
                <div className="flex items-center gap-2">
                  <StarRating 
                    itemId={school.id}
                    itemType="school"
                    initialRating={school.rating}
                  />
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="max-w-7xl mx-auto px-4 py-8">
        {/* About Section */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-6">
          <h2 className="text-gray-900 mb-3">À propos</h2>
          <p className="text-gray-600 leading-relaxed">{school.description}</p>
        </div>

        {/* Programmes Section */}
        <div className="mb-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-gray-900">Formations disponibles ({programmes.length})</h2>
            
            {/* Sort Dropdown */}
            <div className="flex items-center gap-2">
              <ArrowUpDown className="w-4 h-4 text-gray-500" />
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as 'name' | 'places')}
                className="px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent text-gray-700"
              >
                <option value="name">Trier par A-Z</option>
                <option value="places">Trier par Places disponibles</option>
              </select>
            </div>
          </div>
          <div className="space-y-4">
            {sortedProgrammes.map(programme => {
              const isInWishlist = wishlist.includes(programme.id);
              const availableSpots = programme.capacity - programme.enrolled;
              
              return (
                <div
                  key={programme.id}
                  className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 hover:shadow-md transition-shadow"
                >
                  <div className="flex items-start justify-between mb-4">
                    <div className="flex-1">
                      <div className="flex items-start justify-between mb-2">
                        <h3 className="text-gray-900 flex-1 line-clamp-2" style={{ fontSize: '1.125rem', fontWeight: 700 }}>{programme.name}</h3>
                        <button
                          onClick={() => toggleWishlist(programme.id)}
                          className={`p-2 rounded-full transition-colors ${
                            isInWishlist
                              ? 'bg-red-50 text-red-600 hover:bg-red-100'
                              : 'bg-gray-50 text-gray-400 hover:bg-gray-100 hover:text-red-600'
                          }`}
                        >
                          <Heart
                            className={`w-5 h-5 ${isInWishlist ? 'fill-current' : ''}`}
                          />
                        </button>
                      </div>
                      <div className="flex items-center gap-4 flex-wrap text-gray-600 mb-3">
                        <div className="flex items-center gap-2">
                          <GraduationCap className="w-4 h-4" />
                          <span style={{ fontWeight: 600 }}>{programme.level}</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <Clock className="w-4 h-4" />
                          <span style={{ fontWeight: 600 }}>{programme.duration}</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <Calendar className="w-4 h-4" />
                          <span style={{ fontWeight: 600 }}>{programme.startDate}</span>
                        </div>
                      </div>
                      {isAuthenticated && (
                        <div className="mb-3">
                          <StarRating 
                            itemId={programme.id}
                            itemType="programme"
                            initialRating={programme.rating}
                          />
                        </div>
                      )}
                      <p className="text-gray-600 mb-4">{programme.description}</p>
                      <div className="flex items-center gap-2 text-gray-600">
                        <Users className="w-4 h-4" />
                        <span>
                          {availableSpots > 0 ? (
                            <>{availableSpots} places disponibles sur {programme.capacity}</>
                          ) : (
                            <span className="text-red-600">Complet</span>
                          )}
                        </span>
                      </div>
                    </div>
                  </div>
                  <div className="flex gap-3">
                    <button
                      className="flex-1 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors disabled:bg-gray-300 disabled:cursor-not-allowed"
                      disabled={availableSpots === 0}
                    >
                      {availableSpots > 0 ? 'Postuler' : 'Liste d\'attente'}
                    </button>
                    <button className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors">
                      En savoir plus
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}