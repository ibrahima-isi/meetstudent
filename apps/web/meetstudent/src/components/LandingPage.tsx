import { useState } from 'react';
import { Search, MapPin, Star, Filter, LogIn, UserPlus, ArrowUpDown } from 'lucide-react';
import { ImageWithFallback } from './figma/ImageWithFallback';
import { School } from './HomePage';
import { SCHOOLS, CITIES, TYPES } from '../data/schools';
import { PROGRAMMES } from '../data/programmes';

interface LandingPageProps {
  onSchoolClick: (school: School) => void;
  onLoginClick: () => void;
  onRegisterClick: () => void;
}

export function LandingPage({ onSchoolClick, onLoginClick, onRegisterClick }: LandingPageProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCity, setSelectedCity] = useState('Toutes les villes');
  const [selectedType, setSelectedType] = useState('Tous les types');
  const [showFilters, setShowFilters] = useState(false);
  const [sortBy, setSortBy] = useState<'name' | 'city' | 'places'>('name');

  const filteredSchools = SCHOOLS.filter(school => {
    const matchesSearch = school.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                         school.description.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCity = selectedCity === 'Toutes les villes' || school.city === selectedCity;
    const matchesType = selectedType === 'Tous les types' || school.type === selectedType;
    
    return matchesSearch && matchesCity && matchesType;
  });

  // Sort schools
  const sortedSchools = [...filteredSchools].sort((a, b) => {
    if (sortBy === 'name') {
      return a.name.localeCompare(b.name, 'fr');
    } else if (sortBy === 'city') {
      return a.city.localeCompare(b.city, 'fr');
    } else if (sortBy === 'places') {
      // Calculate total available places for each school
      const aPrograms = PROGRAMMES[a.id] || [];
      const bPrograms = PROGRAMMES[b.id] || [];
      const aPlaces = aPrograms.reduce((sum, p) => sum + (p.capacity - p.enrolled), 0);
      const bPlaces = bPrograms.reduce((sum, p) => sum + (p.capacity - p.enrolled), 0);
      return bPlaces - aPlaces; // Descending order
    }
    return 0;
  });

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b border-gray-200 sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-gray-900">MeetStudent</h1>
              <p className="text-gray-600">Trouvez votre établissement idéal</p>
            </div>
            <div className="flex items-center gap-3">
              <button
                onClick={onLoginClick}
                className="flex items-center gap-2 px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
              >
                <LogIn className="w-5 h-5" />
                <span>Connexion</span>
              </button>
              <button
                onClick={onRegisterClick}
                className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
              >
                <UserPlus className="w-5 h-5" />
                <span>S'inscrire</span>
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Hero Section */}
      <div className="bg-gradient-to-br from-indigo-600 to-purple-700 text-white">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
          <div className="text-center max-w-3xl mx-auto">
            <h2 className="mb-4">
              Découvrez les meilleurs établissements du Sénégal
            </h2>
            <p className="text-indigo-100 mb-8">
              Explorez notre catalogue d'écoles, lycées et universités. Comparez les formations et trouvez celle qui vous correspond.
            </p>
          </div>
        </div>
      </div>

      {/* Search & Filters */}
      <div className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="space-y-4">
            {/* Search Bar */}
            <div className="relative">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Rechercher un établissement..."
                className="w-full pl-12 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
              />
            </div>

            {/* Filter and Sort Controls */}
            <div className="flex items-center gap-3 flex-wrap">
              <button
                onClick={() => setShowFilters(!showFilters)}
                className="flex items-center gap-2 px-4 py-2 text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
              >
                <Filter className="w-4 h-4" />
                <span>Filtres</span>
                {(selectedCity !== 'Toutes les villes' || selectedType !== 'Tous les types') && (
                  <span className="ml-2 px-2 py-0.5 bg-indigo-100 text-indigo-700 rounded-full">
                    {[selectedCity !== 'Toutes les villes', selectedType !== 'Tous les types'].filter(Boolean).length}
                  </span>
                )}
              </button>

              {/* Sort Dropdown */}
              <div className="flex items-center gap-2">
                <ArrowUpDown className="w-4 h-4 text-gray-500" />
                <select
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value as 'name' | 'city' | 'places')}
                  className="px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent text-gray-700"
                >
                  <option value="name">Trier par A-Z</option>
                  <option value="city">Trier par Ville</option>
                  <option value="places">Trier par Places disponibles</option>
                </select>
              </div>
            </div>

            {/* Filters */}
            {showFilters && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
                <div>
                  <label className="block text-gray-700 mb-2">Ville</label>
                  <select
                    value={selectedCity}
                    onChange={(e) => setSelectedCity(e.target.value)}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                  >
                    {CITIES.map(city => (
                      <option key={city} value={city}>{city}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-gray-700 mb-2">Type d'établissement</label>
                  <select
                    value={selectedType}
                    onChange={(e) => setSelectedType(e.target.value)}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                  >
                    {TYPES.map(type => (
                      <option key={type} value={type}>{type}</option>
                    ))}
                  </select>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Results */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-6">
          <p className="text-gray-600">
            {sortedSchools.length} établissement{sortedSchools.length !== 1 ? 's' : ''} trouvé{sortedSchools.length !== 1 ? 's' : ''}
          </p>
        </div>

        {/* School Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {sortedSchools.map(school => (
            <div
              key={school.id}
              className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden hover:shadow-md transition-shadow cursor-pointer"
            >
              <div className="relative h-48">
                <ImageWithFallback
                  src={school.image}
                  alt={school.name}
                  className="w-full h-full object-cover"
                />
                <div className="absolute top-3 right-3 px-3 py-1 bg-white rounded-full shadow-sm">
                  <span className="text-indigo-600">{school.type}</span>
                </div>
              </div>
              
              <div className="p-5">
                <h3 className="text-gray-900 mb-2 line-clamp-2" style={{ fontSize: '1.25rem', fontWeight: 700 }}>{school.name}</h3>
                
                <div className="flex items-center gap-1 mb-3">
                  <div className="flex items-center gap-1">
                    <Star className="w-4 h-4 fill-yellow-400 text-yellow-400" />
                    <span className="text-gray-900">{school.rating}</span>
                  </div>
                  <span className="text-gray-500">({school.reviewCount} avis)</span>
                </div>

                <div className="flex items-start gap-2 mb-3">
                  <MapPin className="w-4 h-4 text-gray-400 flex-shrink-0 mt-1" />
                  <div className="text-gray-600">
                    <p>{school.address}</p>
                    <p>{school.city}</p>
                  </div>
                </div>

                <p className="text-gray-600 line-clamp-2 mb-4">
                  {school.description}
                </p>

                <button 
                  onClick={() => onSchoolClick(school)}
                  className="w-full px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
                >
                  Voir les détails
                </button>
              </div>
            </div>
          ))}
        </div>

        {sortedSchools.length === 0 && (
          <div className="text-center py-12">
            <p className="text-gray-600">Aucun établissement trouvé avec ces critères</p>
          </div>
        )}
      </div>
    </div>
  );
}