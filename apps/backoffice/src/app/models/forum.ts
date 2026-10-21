/** Lifecycle of a forum report. */
export type ForumReportStatus = 'OPEN' | 'HANDLED' | 'DISMISSED';

export type ForumTargetType = 'THREAD' | 'REPLY';

/**
 * A report in the moderation queue (`GET /admin/forum/reports`). Every text field is user input: bind it as text
 * only. `excerpt` is the message now, `reportedExcerpt` the message when it was reported (both 200 code points at most).
 */
export interface ForumReport {
  id: number;
  targetType: ForumTargetType;
  targetId: number;
  threadId: number;
  threadTitle: string;
  excerpt: string;
  reportedExcerpt?: string | null;
  /** Set once the author edited the message. */
  targetModifiedAt?: string | null;
  targetHidden: boolean;
  reason: string;
  status: ForumReportStatus;
  /** First name only; absent once the reporter's account is deleted. */
  reporterName?: string | null;
  createdAt: string;
  handledAt?: string | null;
}

/** What a hide/lock call answers: only the moderation flags are used. */
export interface ForumModerated {
  id: number;
  hidden: boolean;
  locked?: boolean;
}
