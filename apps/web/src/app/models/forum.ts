import { Page } from './entities';

/** Who wrote a message, as the API labels it. Absent when the account was deleted. */
export type ForumAuthorRole = 'STUDENT' | 'EXPERT';

/** A thread as the API sends it: no e-mail, last name or user id is ever included. */
export interface ForumThread {
  id: number;
  title: string;
  body: string;
  schoolId?: number;
  schoolName?: string;
  /** First name only; absent when `authorDeleted`. Comes from an account: clean it before display. */
  authorName?: string;
  authorRole?: ForumAuthorRole;
  authorDeleted: boolean;
  /** Computed by the server for the caller: true when the caller wrote it. */
  mine: boolean;
  locked: boolean;
  hidden: boolean;
  replyCount: number;
  createdAt: string;
  modifiedAt?: string;
}

export interface ForumReply {
  id: number;
  threadId: number;
  body: string;
  authorName?: string;
  authorRole?: ForumAuthorRole;
  authorDeleted: boolean;
  mine: boolean;
  hidden: boolean;
  createdAt: string;
  modifiedAt?: string;
}

export interface ForumThreadRequest {
  title: string;
  body: string;
  /** Read on creation only: a thread keeps its school. */
  schoolId?: number;
}

export type ForumThreadPage = Page<ForumThread>;
export type ForumReplyPage = Page<ForumReply>;

/** Server limits, mirrored so a visitor is told before sending. */
export const FORUM_MAX_TITLE = 150;
export const FORUM_MAX_BODY = 5000;
