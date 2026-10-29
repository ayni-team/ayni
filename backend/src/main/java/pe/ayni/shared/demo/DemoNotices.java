package pe.ayni.shared.demo;

import java.util.UUID;

/** What the history of the {@code demo} profile asks of notifications. */
public interface DemoNotices {

  /**
   * Marks as read the oldest notices of a person, as if they had opened them, and leaves the rest
   * unread.
   *
   * @return how many were marked
   */
  int markOldestRead(UUID recipientId, int count);
}
