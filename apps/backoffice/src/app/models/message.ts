/** Delivery state of the notification mail of a contact message. UNKNOWN: received before the tracking existed. */
export type MailStatus = 'UNKNOWN' | 'PENDING' | 'SENT' | 'FAILED' | 'SKIPPED';

/** A message sent through the public contact form (`GET /admin/messages`). */
export interface ContactMessage {
  id: number;
  email: string;
  subject: string;
  message: string;
  language?: string | null;
  /** ISO instant. */
  createdAt: string;
  mailStatus: MailStatus;
  mailAttempts: number;
  /** ISO instant of the last successful send, null if never sent. */
  mailSentAt: string | null;
}
