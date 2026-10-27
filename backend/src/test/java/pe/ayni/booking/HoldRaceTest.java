package pe.ayni.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HourBlockStatus;

/**
 * Several students choosing the same free hour at the same instant, each on a connection of their
 * own. Whoever reads the hour after the winner committed is refused by the block; whoever read it
 * free alongside the winner loses on the version column. Either way exactly one of them holds it.
 */
class HoldRaceTest extends BookingScenario {

  private static final int STUDENTS = 6;

  @Test
  @DisplayName("of several students holding the same hour at once, exactly one gets it")
  void exactlyOneStudentHoldsTheHour() throws Exception {

    List<UUID> students = new ArrayList<>();
    for (int i = 0; i < STUDENTS; i++) {
      students.add(UUID.randomUUID());
    }

    CountDownLatch startingGun = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(STUDENTS);
    try {
      List<Future<Integer>> answers = new ArrayList<>();
      for (UUID student : students) {
        answers.add(
            pool.submit(
                () -> {
                  startingGun.await();
                  return hold(student, tomorrowAt(10), 1)
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      startingGun.countDown();

      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> answer : answers) {
        statuses.add(answer.get(30, TimeUnit.SECONDS));
      }

      assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
      assertThat(statuses).filteredOn(status -> status == 409).hasSize(STUDENTS - 1);
    } finally {
      pool.shutdownNow();
    }

    HourBlock block = blockAt(tomorrowAt(10));
    assertThat(block.getStatus()).isEqualTo(HourBlockStatus.HELD);
    assertThat(students).contains(block.getHeldBy());
  }
}
