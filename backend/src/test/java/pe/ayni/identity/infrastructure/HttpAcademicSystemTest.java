package pe.ayni.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.ayni.config.AcademicDocuments;
import pe.ayni.identity.domain.model.AcademicSystemNotConfigured;

/**
 * The simulated academic system read from an address of another service, as a deployment reads it
 * from a GitHub Pages site (ADR 0006, its second phase): the same documents and the same answers as
 * from the folder, behind the same port. A real HTTP server of the JDK publishes the documents, so
 * what is tested is the reading over HTTP; the time a copy is kept runs on a clock of the test. The
 * scenarios of {@code features/academic-system-documents.feature} about an address run here, each
 * with the same name.
 */
class HttpAcademicSystemTest {

    private static final String NORA = "nora.quispe@uni.edu.pe";

    private final ObjectMapper json = new ObjectMapper();
    private final MovableClock clock = new MovableClock();

    /** What the server answers for each file name; anything else is a 404. */
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final AtomicInteger requests = new AtomicInteger();

    private HttpServer server;

    @TempDir Path folder;

    private record Answer(int status, String body, Duration delay) {
    }

    @BeforeEach
    void startTheServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/academic/",
                exchange -> {
                    requests.incrementAndGet();
                    String name = exchange.getRequestURI().getPath().substring("/academic/".length());
                    Answer answer = answers.getOrDefault(name, new Answer(404, "", Duration.ZERO));
                    try {
                        Thread.sleep(answer.delay().toMillis());
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(answer.status(), body.length == 0 ? -1 : body.length);
                    if (body.length > 0) {
                        exchange.getResponseBody().write(body);
                    }
                    exchange.close();
                });
        server.start();
    }

    @AfterEach
    void stopTheServer() {
        server.stop(0);
    }

    private MockAcademicSystemAdapter adapter() {
        return adapter(Duration.ofSeconds(2));
    }

    private MockAcademicSystemAdapter adapter(Duration timeout) {
        // The address without its trailing slash, as a person may write it.
        URI address = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/academic");
        return new MockAcademicSystemAdapter(
                new HttpAcademicDocumentSource(address, timeout, Duration.ofMinutes(1), clock),
                folder,
                json);
    }

    private void publish(String university, String document) {
        answers.put(university + ".json", new Answer(200, document, Duration.ZERO));
    }

    @Test
    @DisplayName("A document published at an address is read as from the folder")
    void aDocumentPublishedAtAnAddressIsRead() {
        publish("UNI", AcademicDocuments.document("UNI", AcademicDocuments.student(NORA, "N1")));

        assertThat(adapter().fetchProfile("uni", "Nora.Quispe@UNI.edu.pe").studentCode()).isEqualTo("N1");
        assertThat(adapter().fetchUniversity("UNI").code()).isEqualTo("UNI");
    }

    @Test
    @DisplayName("Without its document at the address a university has no academic system")
    void withoutItsDocumentAtTheAddress() {
        assertThatThrownBy(() -> adapter().fetchUniversity("OTRA"))
                .isInstanceOf(AcademicSystemNotConfigured.class);
    }

    @Test
    @DisplayName("A document edited at the address is used once its copy is a minute old")
    void aDocumentEditedAtTheAddressIsUsedAfterAMinute() {
        MockAcademicSystemAdapter adapter = adapter();
        publish("UNI", AcademicDocuments.document("UNI", AcademicDocuments.student(NORA, "N1")));
        assertThat(adapter.fetchProfile("UNI", NORA).studentCode()).isEqualTo("N1");

        publish("UNI", AcademicDocuments.document("UNI", AcademicDocuments.student(NORA, "N2")));
        clock.advance(Duration.ofSeconds(59));
        // Within the minute the copy answers, and the server is not asked again.
        assertThat(adapter.fetchProfile("UNI", NORA).studentCode()).isEqualTo("N1");
        assertThat(requests).hasValue(1);

        clock.advance(Duration.ofSeconds(2));
        assertThat(adapter.fetchProfile("UNI", NORA).studentCode()).isEqualTo("N2");
        assertThat(requests).hasValue(2);
    }

    @Test
    @DisplayName("While the address does not answer, the last copy read is used")
    void whileTheAddressDoesNotAnswerTheLastCopyIsUsed() {
        MockAcademicSystemAdapter adapter = adapter();
        publish("UNI", AcademicDocuments.document("UNI", AcademicDocuments.student(NORA, "N1")));
        assertThat(adapter.fetchProfile("UNI", NORA).studentCode()).isEqualTo("N1");

        answers.put("UNI.json", new Answer(503, "", Duration.ZERO));
        clock.advance(Duration.ofHours(5));

        assertThat(adapter.fetchProfile("UNI", NORA).studentCode()).isEqualTo("N1");
    }

    @Test
    @DisplayName("With no copy, an address that does not answer is a mistake of the platform")
    void withNoCopyAnAddressThatDoesNotAnswerIsAMistake() {
        answers.put("UNI.json", new Answer(200, AcademicDocuments.document("UNI"), Duration.ofSeconds(2)));

        assertThatThrownBy(() -> adapter(Duration.ofMillis(300)).fetchProfile("UNI", NORA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNI")
                .hasMessageContaining("cannot be read")
                .hasMessageNotContaining("nora");

        answers.put("UNI.json", new Answer(500, "", Duration.ZERO));
        assertThatThrownBy(() -> adapter().fetchUniversity("UNI"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be read");
    }

    @Test
    @DisplayName("The local students are read from the folder, also with an address")
    void theLocalStudentsAreReadFromTheFolder() throws IOException {
        publish("UNI", AcademicDocuments.document("UNI", AcademicDocuments.student(NORA, "N1")));
        Files.writeString(
                folder.resolve("UNI.local.json"),
                AcademicDocuments.document("UNI", AcademicDocuments.student("lead@uni.edu.pe", "L1")));
        // Never asked of the address: a real person added for a demonstration is never published.
        answers.put("UNI.local.json", new Answer(200, "{ not json", Duration.ZERO));

        MockAcademicSystemAdapter adapter = adapter();
        assertThat(adapter.fetchProfile("UNI", "lead@uni.edu.pe").studentCode()).isEqualTo("L1");
        assertThat(adapter.fetchProfile("UNI", NORA).studentCode()).isEqualTo("N1");
        assertThat(requests).hasValue(1);
    }

    /** A clock the test moves by hand. */
    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-10-25T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
