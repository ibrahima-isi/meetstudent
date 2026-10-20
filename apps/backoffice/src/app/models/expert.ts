/** Approval status of an Expert (teacher) account. */
export type ExpertStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** An Expert account in the approval queue (`GET /admin/experts`). */
export interface ExpertAccount {
  id: number;
  firstname: string | null;
  lastname: string | null;
  email: string;
  /** The specialty given at registration. */
  qualification: string | null;
  /** Local date-time without zone, as sent by the API. */
  registeredAt: string;
  status: ExpertStatus;
  /** Reason given at rejection, absent otherwise. */
  reason?: string | null;
  decisionDate?: string | null;
}
