/** A message sent through the public contact form (`GET /admin/messages`). */
export interface ContactMessage {
  id: number;
  email: string;
  subject: string;
  message: string;
  language?: string | null;
  /** ISO instant. */
  createdAt: string;
}
