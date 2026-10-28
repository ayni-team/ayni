package pe.ayni.identity.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The documents of the simulated academic system in a folder of the backend: the repository's
 * {@code backend/academic-directory} in development, a volume in a deployment. Read each time they
 * are asked, so a file added or edited is used at once.
 */
final class FolderAcademicDocumentSource
        implements AcademicDocumentSource {

    private final Path directory;

    FolderAcademicDocumentSource(
            Path directory) {

        this.directory =
                Objects.requireNonNull(
                        directory,
                        "directory must not be null");
    }

    @Override
    public Optional<byte[]> read(
            String name) throws IOException {

        Path file = directory.resolve(name);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(Files.readAllBytes(file));
    }

    @Override
    public String describe() {
        return "the folder " + directory.toAbsolutePath();
    }
}
