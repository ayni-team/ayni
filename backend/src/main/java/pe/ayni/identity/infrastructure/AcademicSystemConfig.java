package pe.ayni.identity.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where the simulated academic system (ADR 0006) reads its documents from.
 *
 * <p>With {@code ayni.identity.academic-directory-url} empty, as in development, from the folder
 * {@code ayni.identity.academic-directory}. Set (for instance
 * {@code https://ayni-team.github.io/ayni-academic-system/}), from that address, the way a
 * university's own system would be another service: each document a static file under it, read
 * with a timeout and kept for {@code ayni.identity.academic-directory-fresh-for}. The local
 * students of a demonstration stay in the folder either way.
 */
@Configuration
class AcademicSystemConfig {

    private static final Logger log =
            LoggerFactory.getLogger(AcademicSystemConfig.class);

    @Bean
    MockAcademicSystemAdapter academicSystem(
            @Value("${ayni.identity.academic-directory}") Path directory,
            @Value("${ayni.identity.academic-directory-url:}") String address,
            @Value("${ayni.identity.academic-directory-timeout:PT3S}") Duration timeout,
            @Value("${ayni.identity.academic-directory-fresh-for:PT1M}") Duration freshFor,
            ObjectMapper json) {

        AcademicDocumentSource documents =
                address.isBlank()
                        ? new FolderAcademicDocumentSource(directory)
                        : new HttpAcademicDocumentSource(
                                URI.create(address.trim()),
                                timeout,
                                freshFor,
                                Clock.systemUTC());
        log.info(
                "The academic system reads its documents from {}",
                documents.describe());
        return new MockAcademicSystemAdapter(documents, directory, json);
    }
}
