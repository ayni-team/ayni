package pe.ayni.identity.infrastructure;

import java.io.IOException;
import java.util.Optional;

/**
 * Where the documents of the simulated academic system (ADR 0006) are read from: a folder of the
 * backend, or the address of another service that publishes them.
 *
 * <p>A document is named as its file, {@code UPC.json}. One that does not exist is empty; one that
 * exists and cannot be read now is an {@link IOException}, which {@link MockAcademicSystemAdapter}
 * reports as a mistake of the platform.
 */
interface AcademicDocumentSource {

    /** The content of the named document, or empty when there is none. */
    Optional<byte[]> read(String name) throws IOException;

    /** Where the documents are, for the log: a folder or an address, never a document. */
    String describe();
}
