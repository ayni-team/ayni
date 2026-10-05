package pe.ayni.skills.application;

import java.io.InputStream;
import java.util.NoSuchElementException;

/**
 * Where the files a student attaches as evidence live.
 *
 * <p>The database keeps only the key this port returns, so what is stored can move from the disk of
 * the server to an object store without a table changing. Every key belongs to one university: a
 * key is never readable from another one, even if somebody guessed it.
 */
public interface EvidenceStoragePort {

  /**
   * Stores the content and returns the key it can be read with again.
   *
   * <p>The key is generated here and never built from the name of the file: a name comes from the
   * student, and a name that reaches a path is a way out of the folder it was meant to stay in.
   *
   * @throws EvidenceStorageException when the content cannot be written
   */
  String store(String tenantId, InputStream content);

  /**
   * Opens the content stored under the key.
   *
   * @throws NoSuchElementException when nothing is stored under it for this university
   * @throws EvidenceStorageException when the content exists but cannot be read
   */
  InputStream open(String tenantId, String storageKey);

  /**
   * Removes the content, if it is there. Used to take back what was stored for a submission that
   * then failed, so a refused request does not leave files behind.
   *
   * @throws EvidenceStorageException when the content exists but cannot be removed
   */
  void delete(String tenantId, String storageKey);
}
