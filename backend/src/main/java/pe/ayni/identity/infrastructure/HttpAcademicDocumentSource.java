package pe.ayni.identity.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The documents of the simulated academic system published by another service: static files under
 * one address, {@code <address>/UPC.json}, such as a GitHub Pages site (ADR 0006,
 * {@code ayni.identity.academic-directory-url}).
 *
 * <p>Each answer is kept for {@code freshFor}, so a sign up does not wait for the other service
 * every time and a document edited there is used within that time. When the service cannot be
 * reached, or answers anything but the document or a 404, the last copy read is used, however old,
 * and the log says so; with no copy, the read fails. A 404 is a university without a document.
 *
 * <p>The time it keeps a copy is measured with the real clock, not the application's, which the
 * demonstration moves.
 */
final class HttpAcademicDocumentSource
        implements AcademicDocumentSource {

    private static final Logger log =
            LoggerFactory.getLogger(HttpAcademicDocumentSource.class);

    private final URI base;
    private final HttpClient http;
    private final Duration timeout;
    private final Duration freshFor;
    private final Clock clock;
    private final ConcurrentMap<String, Copy> copies =
            new ConcurrentHashMap<>();

    /** What the service answered for a document, and when. */
    private record Copy(
            Optional<byte[]> content,
            Instant readAt) {
    }

    HttpAcademicDocumentSource(
            URI base,
            Duration timeout,
            Duration freshFor,
            Clock clock) {

        Objects.requireNonNull(
                base,
                "base must not be null");
        String address = base.toString();
        this.base =
                URI.create(
                        address.endsWith("/")
                                ? address
                                : address + "/");
        this.timeout =
                Objects.requireNonNull(
                        timeout,
                        "timeout must not be null");
        this.freshFor =
                Objects.requireNonNull(
                        freshFor,
                        "freshFor must not be null");
        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock must not be null");
        this.http =
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    @Override
    public Optional<byte[]> read(
            String name) throws IOException {

        Instant now = clock.instant();
        Copy kept = copies.get(name);
        if (kept != null
                && now.isBefore(kept.readAt().plus(freshFor))) {
            return kept.content();
        }

        try {
            Optional<byte[]> content = fetch(name);
            copies.put(name, new Copy(content, now));
            return content;
        } catch (IOException unreachable) {
            if (kept == null) {
                throw unreachable;
            }
            // The name of the document and the kind of failure only: never what it holds.
            log.warn(
                    "The academic system did not answer {} ({}); using the copy read at {}",
                    name,
                    unreachable.getClass().getSimpleName(),
                    kept.readAt());
            return kept.content();
        }
    }

    private Optional<byte[]> fetch(
            String name) throws IOException {

        HttpRequest request =
                HttpRequest.newBuilder(base.resolve(name))
                        .timeout(timeout)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

        HttpResponse<byte[]> response;
        try {
            response =
                    http.send(
                            request,
                            HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Interrupted while reading " + name,
                    interrupted);
        }

        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new IOException(
                    "The academic system answered " + response.statusCode() + " for " + name);
        }
        return Optional.of(response.body());
    }

    @Override
    public String describe() {
        return "the address " + base;
    }
}
