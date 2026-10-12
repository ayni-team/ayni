package pe.ayni.skills.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.ayni.skills.application.EvidenceStoragePort;
import pe.ayni.skills.application.EvidenceStorageException;

/**
 * Keeps the evidence on the disk of the server, one folder per university.
 *
 * <p>Good enough while there is one server. The folder is the property {@code
 * ayni.skills.evidence-dir}; behind a load balancer, or in a container that is replaced, it has to
 * be a volume, or this adapter has to give way to one that talks to an object store, which the port
 * allows without anything else changing.
 *
 * <p>The key is {@code <university>/<random uuid>}: no part of it comes from the student, and every
 * key that reaches the disk is checked to stay inside the folder before it is used.
 */
@Component
public class LocalDiskEvidenceStorageAdapter implements EvidenceStoragePort {

  private static final Pattern SAFE_TENANT = Pattern.compile("[A-Za-z0-9_-]{1,32}");

  private final Path root;

  LocalDiskEvidenceStorageAdapter(
      @Value("${ayni.skills.evidence-dir:${java.io.tmpdir}/ayni/evidence}") String directory) {
    this.root = Path.of(directory).toAbsolutePath().normalize();
  }

  @Override
  public String store(String tenantId, InputStream content) {
    Objects.requireNonNull(content, "content must not be null");
    String key = requireSafeTenant(tenantId) + "/" + UUID.randomUUID();
    Path target = resolve(tenantId, key);
    try {
      Files.createDirectories(target.getParent());
      Files.copy(content, target);
    } catch (IOException failure) {
      // Do not leave half a file behind: nothing would ever point at it.
      try {
        Files.deleteIfExists(target);
      } catch (IOException ignored) {
        // The original failure is the one worth reporting.
      }
      throw new EvidenceStorageException("could not store the evidence", failure);
    }
    return key;
  }

  @Override
  public InputStream open(String tenantId, String storageKey) {
    Path target = resolve(tenantId, storageKey);
    try {
      return Files.newInputStream(target);
    } catch (NoSuchFileException missing) {
      throw new NoSuchElementException("no evidence is stored under that key");
    } catch (IOException failure) {
      throw new EvidenceStorageException("could not read the evidence", failure);
    }
  }

  @Override
  public void delete(String tenantId, String storageKey) {
    Path target = resolve(tenantId, storageKey);
    try {
      Files.deleteIfExists(target);
    } catch (IOException failure) {
      throw new EvidenceStorageException("could not remove the evidence", failure);
    }
  }

  /** The path of a key, after proving it belongs to the university and stays in the folder. */
  private Path resolve(String tenantId, String storageKey) {
    String tenant = requireSafeTenant(tenantId);
    Objects.requireNonNull(storageKey, "storageKey must not be null");
    if (!storageKey.startsWith(tenant + "/")) {
      throw new NoSuchElementException("no evidence is stored under that key");
    }
    Path tenantFolder = root.resolve(tenant);
    Path target = root.resolve(storageKey).normalize();
    // Inside the university's folder and not the folder itself: a key such as "UPC/" would
    // otherwise point at the directory, and deleting it would remove the folder of a university.
    if (!target.startsWith(tenantFolder) || target.equals(tenantFolder)) {
      throw new NoSuchElementException("no evidence is stored under that key");
    }
    return target;
  }

  private static String requireSafeTenant(String tenantId) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    if (!SAFE_TENANT.matcher(tenantId).matches()) {
      throw new IllegalArgumentException("the university code cannot be used as a folder name");
    }
    return tenantId;
  }
}
