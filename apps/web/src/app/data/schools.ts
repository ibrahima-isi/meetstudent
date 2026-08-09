import { School } from '@models/entities';

export const SCHOOLS: School[] = [
  {
    id: 1,
    name: 'Université Cheikh Anta Diop (UCAD)',
    code: 'UCAD',
    type: 'Université Publique',
    address: {
      location: 'BP 5005',
      city: 'Dakar',
      country: 'Sénégal'
    },
    rating: 4.7,
    reviewCount: 842,
    coverImageUrl: 'https://images.unsplash.com/photo-1576495199011-eb94736d05d6?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHx1bml2ZXJzaXR5JTIwY2FtcHVzfGVufDF8fHx8MTc2MzMwMDY4NHww&ixlib=rb-4.1.0&q=80&w=1080',
    description: 'La plus grande université du Sénégal, offrant une formation pluridisciplinaire de qualité',
  },
  {
    id: 2,
    name: 'Université Gaston Berger (UGB)',
    code: 'UGB',
    type: 'Université Publique',
    address: {
      location: 'Route de Ngallèle',
      city: 'Saint-Louis',
      country: 'Sénégal'
    },
    rating: 4.6,
    reviewCount: 567,
    coverImageUrl: 'https://images.unsplash.com/photo-1562774053-701939374585?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxjb2xsZWdlJTIwbGlicmFyeXxlbnwxfHx8fDE3NjMzMDY4Mzd8MA&ixlib=rb-4.1.0&q=80&w=1080',
    description: 'Université de référence dans le nord du Sénégal, excellence en sciences et lettres',
  }
];

export const CITIES = [
  'Toutes les villes',
  'Dakar',
  'Saint-Louis',
  'Thiès',
  'Bambey',
  'Ziguinchor',
  'Kaolack',
];

export const TYPES = [
  'Tous les types',
  'Université Publique',
  'Université Privée',
  'Grande École',
  'École Privée',
  'Institut Public',
];
