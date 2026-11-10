package pe.ayni.sessions.infrastructure;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ayni.sessions.application.PracticeRooms;

/**
 * Forgets the practice rooms whose four hours are over, and closes the channels of their boards,
 * even when nobody asks for them again. Nothing is stored, so there is no university to bind.
 */
@Component
class PracticeRoomsSweepJob {

  private final PracticeRooms practice;

  PracticeRoomsSweepJob(PracticeRooms practice) {
    this.practice = practice;
  }

  @Scheduled(
      fixedDelayString = "${ayni.sessions.practice-rooms.sweep-delay:PT1M}",
      initialDelayString = "${ayni.sessions.practice-rooms.sweep-delay:PT1M}")
  void forgetExpired() {
    practice.forgetExpired();
  }
}
