import { ShoppingCart, X, GraduationCap } from 'lucide-react';
import { useState, useEffect } from 'react';
import { PROGRAMMES } from '../data/programmes';

interface WishlistCartProps {
  isAuthenticated: boolean;
  onLoginPrompt?: () => void;
}

export function WishlistCart({ isAuthenticated, onLoginPrompt }: WishlistCartProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [wishlist, setWishlist] = useState<number[]>([]);

  useEffect(() => {
    const updateWishlist = () => {
      if (isAuthenticated) {
        const saved = localStorage.getItem('wishlist');
        setWishlist(saved ? JSON.parse(saved) : []);
      } else {
        setWishlist([]);
      }
    };

    updateWishlist();
    
    // Listen for wishlist updates
    window.addEventListener('wishlistUpdated', updateWishlist);
    
    return () => {
      window.removeEventListener('wishlistUpdated', updateWishlist);
    };
  }, [isAuthenticated]);

  const handleCartClick = () => {
    if (!isAuthenticated && onLoginPrompt) {
      onLoginPrompt();
      return;
    }
    setIsOpen(!isOpen);
  };

  const removeFromWishlist = (programmeId: number) => {
    const newWishlist = wishlist.filter(id => id !== programmeId);
    setWishlist(newWishlist);
    localStorage.setItem('wishlist', JSON.stringify(newWishlist));
    window.dispatchEvent(new Event('wishlistUpdated'));
  };

  // Get all programmes from all schools
  const allProgrammes = Object.values(PROGRAMMES).flat();
  const wishlistProgrammes = allProgrammes.filter(p => wishlist.includes(p.id));

  return (
    <div className="relative">
      <button
        onClick={handleCartClick}
        className="relative flex items-center gap-2 px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
      >
        <ShoppingCart className="w-5 h-5" />
        <span>Souhaits</span>
        {isAuthenticated && wishlist.length > 0 && (
          <span className="absolute -top-1 -right-1 w-5 h-5 bg-indigo-600 text-white rounded-full flex items-center justify-center">
            {wishlist.length}
          </span>
        )}
      </button>

      {/* Cart Dropdown */}
      {isOpen && isAuthenticated && (
        <>
          {/* Backdrop */}
          <div 
            className="fixed inset-0 z-40"
            onClick={() => setIsOpen(false)}
          />
          
          {/* Cart Panel */}
          <div className="absolute left-0 top-full mt-2 w-96 bg-white rounded-xl shadow-xl border border-gray-200 z-50 max-h-[80vh] overflow-hidden flex flex-col">
            <div className="p-4 border-b border-gray-200">
              <div className="flex items-center justify-between">
                <h3 className="text-gray-900">Mes formations souhaitées</h3>
                <button
                  onClick={() => setIsOpen(false)}
                  className="p-1 hover:bg-gray-100 rounded-lg transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
              <p className="text-gray-600 mt-1">
                {wishlist.length} formation{wishlist.length !== 1 ? 's' : ''}
              </p>
            </div>

            <div className="flex-1 overflow-y-auto">
              {wishlistProgrammes.length === 0 ? (
                <div className="p-8 text-center">
                  <GraduationCap className="w-12 h-12 text-gray-300 mx-auto mb-3" />
                  <p className="text-gray-600">Aucune formation dans vos souhaits</p>
                  <p className="text-gray-500 mt-1">
                    Ajoutez des formations pour les retrouver facilement
                  </p>
                </div>
              ) : (
                <div className="p-4 space-y-3">
                  {wishlistProgrammes.map(programme => (
                    <div
                      key={programme.id}
                      className="flex items-start gap-3 p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors"
                    >
                      <GraduationCap className="w-5 h-5 text-indigo-600 flex-shrink-0 mt-1" />
                      <div className="flex-1 min-w-0">
                        <h4 className="text-gray-900 line-clamp-2 mb-1">
                          {programme.name}
                        </h4>
                        <p className="text-gray-600" style={{ fontWeight: 600 }}>
                          {programme.level} • {programme.duration}
                        </p>
                      </div>
                      <button
                        onClick={() => removeFromWishlist(programme.id)}
                        className="p-1 hover:bg-white rounded-lg transition-colors flex-shrink-0"
                      >
                        <X className="w-4 h-4 text-gray-500 hover:text-red-600" />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {wishlistProgrammes.length > 0 && (
              <div className="p-4 border-t border-gray-200 bg-gray-50">
                <button className="w-full px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors">
                  Comparer les formations
                </button>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}