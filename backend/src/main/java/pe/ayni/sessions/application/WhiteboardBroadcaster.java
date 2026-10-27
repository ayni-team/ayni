package pe.ayni.sessions.application;

import java.util.UUID;

/**
 * Tells the participants connected to a whiteboard what changed. Implemented by the live channel
 * in {@code interfaces}; called after the change committed, in the order the versions were given.
 */
public interface WhiteboardBroadcaster {

  /** An operation was applied. Sent to both participants, its sender included. */
  void applied(AppliedOperation operation);

  /** The whole board was replaced. */
  void replaced(WhiteboardView board);

  /** The session is over: the channels of its board are closed. */
  void closed(UUID sessionId);
}
