import { useApiMutation, useApiQuery } from '@/shared/api';

/** The case as it was submitted, with its sessions and the alerts of the audit. */
export function useRecognitionCase(id: string) {
  return useApiQuery(['recognition', 'case', { id }], '/api/v1/coordinator/recognition/requests/{id}', {
    path: { id },
    enabled: Boolean(id),
  });
}

/** The evidence of one session: what was taught, the schedule and the attendance of both. */
export function useSessionEvidence(id: string, sessionId: string | undefined) {
  return useApiQuery(
    ['recognition', 'evidence', { id, sessionId }],
    '/api/v1/coordinator/recognition/requests/{id}/sessions/{sessionId}',
    { path: { id, sessionId: sessionId ?? '' }, enabled: Boolean(id && sessionId) },
  );
}

/** Approving or rejecting. It refreshes the queue and the case, decided or not. */
export function useDecide() {
  return useApiMutation('post', '/api/v1/coordinator/recognition/requests/{id}/decision', {
    invalidates: [['recognition']],
  });
}
