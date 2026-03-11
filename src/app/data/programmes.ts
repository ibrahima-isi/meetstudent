import { Program } from '@models/entities';

export const PROGRAMMES: { [key: number]: Program[] } = {
  1: [
    {
      id: 101,
      name: 'Licence en Mathématiques',
      code: 'LMAT',
      duration: 3,
      level: 'Licence',
      rating: 4.7,
      reviewCount: 142,
      description: 'Formation approfondie en mathématiques pures et appliquées, algèbre et analyse',
      startDate: 'Octobre 2025',
      capacity: 200,
      enrolled: 185,
    }
  ],
  2: [
    {
      id: 201,
      name: 'Licence en Sciences Économiques et Gestion',
      code: 'LSEG',
      duration: 3,
      level: 'Licence',
      rating: 4.6,
      reviewCount: 98,
      description: 'Économie, gestion d\'entreprise et comptabilité',
      startDate: 'Octobre 2025',
      capacity: 180,
      enrolled: 167,
    }
  ]
};
