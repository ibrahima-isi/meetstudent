/** What the public site shows on its About and Contact pages. */
export interface SiteSettings {
  contactEmail: string;
  aboutFr: string;
  aboutEn: string;
  /** Social links: full https URLs, null when not set. Sending '' erases one. */
  whatsappUrl?: string | null;
  telegramUrl?: string | null;
  discordUrl?: string | null;
  facebookUrl?: string | null;
  twitterUrl?: string | null;
  instagramUrl?: string | null;
  tiktokUrl?: string | null;
}
