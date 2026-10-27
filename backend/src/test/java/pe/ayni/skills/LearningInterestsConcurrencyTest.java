package pe.ayni.skills;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.config.SignedInPeople.Person;

/**
 * The same student sending their interests twice at the same moment, a double click, over HTTP
 * against a real PostgreSQL.
 *
 * <p>Both must be answered and the student must end with one interest per item and one skill per
 * course. Without the lock both read that the student has none, both add the same rows, and the
 * unique keys answer the second with a 500. Take {@code learningInterests.lockInterestsOf} out of
 * {@code CompleteOnboardingUseCase} and {@code DeclareInterestsUseCase} and these tests fail.
 */
class LearningInterestsConcurrencyTest extends OnboardingScenario {

  private static final int ROUNDS = 5;

  @Test
  @DisplayName("The last step of the onboarding sent twice at once")
  void theLastStepOfTheOnboardingSentTwiceAtOnce() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < ROUNDS; round++) {
        Person student = aNewStudent();
        completeProfile(student);
        importRecord(student);

        List<Integer> answers =
            together(
                pool,
                () ->
                    declare(student, List.of(physics.getId(), calculus.getId()), List.of(databases.getId()))
                        .andReturn()
                        .getResponse()
                        .getStatus());

        assertThat(answers).containsExactly(200, 200);
        assertThat(stepOf(student)).isEqualTo("COMPLETED");
        assertThat(storedNeedsOf(student)).containsExactlyInAnyOrder(physics.getId(), calculus.getId());
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from skills.offered_skills where tenant_id = ? and tutor_id = ?",
                    Integer.class,
                    tenant,
                    student.id()))
            .isEqualTo(1);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("The same needs saved twice at once")
  void theSameNeedsSavedTwiceAtOnce() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < ROUNDS; round++) {
        Person student = aNewStudent();
        completeProfile(student);
        importRecord(student);
        declare(student, List.of(physics.getId()), List.of()).andReturn();

        List<Integer> answers =
            together(
                pool,
                () ->
                    replaceNeeds(student, List.of(calculus.getId(), python.getId()))
                        .andReturn()
                        .getResponse()
                        .getStatus());

        assertThat(answers).containsExactly(200, 200);
        assertThat(storedNeedsOf(student)).containsExactlyInAnyOrder(calculus.getId(), python.getId());
      }
    } finally {
      pool.shutdownNow();
    }
  }

  /** Runs the request twice, both released at the same moment, and answers both statuses. */
  private static List<Integer> together(ExecutorService pool, Callable<Integer> request)
      throws Exception {
    CyclicBarrier barrier = new CyclicBarrier(2);
    List<Future<Integer>> attempts = new ArrayList<>();
    for (int attempt = 0; attempt < 2; attempt++) {
      attempts.add(
          pool.submit(
              () -> {
                barrier.await();
                return request.call();
              }));
    }
    List<Integer> statuses = new ArrayList<>();
    for (Future<Integer> answer : attempts) {
      statuses.add(answer.get());
    }
    return statuses;
  }
}
