import { Star } from 'lucide-react';
import { useState } from 'react';

interface StarRatingProps {
  itemId: number;
  itemType: 'school' | 'programme';
  initialRating?: number;
  onRate?: (rating: number) => void;
  readonly?: boolean;
}

export function StarRating({ itemId, itemType, initialRating = 0, onRate, readonly = false }: StarRatingProps) {
  const [rating, setRating] = useState<number>(() => {
    const savedRatings = localStorage.getItem(`${itemType}_ratings`);
    if (savedRatings) {
      const ratings = JSON.parse(savedRatings);
      return ratings[itemId] || initialRating;
    }
    return initialRating;
  });
  const [hoverRating, setHoverRating] = useState(0);

  const handleClick = (value: number) => {
    if (readonly) return;
    
    setRating(value);
    
    // Save to localStorage
    const savedRatings = localStorage.getItem(`${itemType}_ratings`);
    const ratings = savedRatings ? JSON.parse(savedRatings) : {};
    ratings[itemId] = value;
    localStorage.setItem(`${itemType}_ratings`, JSON.stringify(ratings));
    
    if (onRate) {
      onRate(value);
    }
  };

  const handleMouseEnter = (value: number) => {
    if (!readonly) {
      setHoverRating(value);
    }
  };

  const handleMouseLeave = () => {
    if (!readonly) {
      setHoverRating(0);
    }
  };

  const displayRating = hoverRating || rating;

  return (
    <div className="flex items-center gap-1">
      {[1, 2, 3, 4, 5].map((value) => (
        <button
          key={value}
          onClick={() => handleClick(value)}
          onMouseEnter={() => handleMouseEnter(value)}
          onMouseLeave={handleMouseLeave}
          disabled={readonly}
          className={`${readonly ? 'cursor-default' : 'cursor-pointer hover:scale-110'} transition-transform`}
        >
          <Star
            className={`w-5 h-5 ${
              value <= displayRating
                ? 'fill-yellow-400 text-yellow-400'
                : 'fill-gray-200 text-gray-200'
            }`}
          />
        </button>
      ))}
      {rating > 0 && (
        <span className="ml-2 text-gray-600">
          {rating}/5
        </span>
      )}
    </div>
  );
}
