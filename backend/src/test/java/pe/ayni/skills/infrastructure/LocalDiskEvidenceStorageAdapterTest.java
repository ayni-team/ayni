package pe.ayni.skills.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.ayni.skills.application.EvidenceStorageException;

/** Evidence on a real folder: what is stored comes back, and stays within its university. */
class LocalDiskEvidenceStorageAdapterTest {

  private static final String UPC = "UPC";
  private static final String UTEC = "UTEC";

  @TempDir Path folder;

  private LocalDiskEvidenceStorageAdapter storage() {
    return new LocalDiskEvidenceStorageAdapter(folder.toString());
  }

  private static InputStream content(String text) {
    return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }

  private static String read(InputStream stream) throws IOException {
    try (stream) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  @DisplayName("what is stored comes back byte for byte")
  void whatIsStoredComesBack() throws IOException {
    LocalDiskEvidenceStorageAdapter storage = storage();

    String key = storage.store(UPC, content("my portfolio"));

    assertThat(read(storage.open(UPC, key))).isEqualTo("my portfolio");
  }

  @Test
  @DisplayName("the key belongs to the university and none of it comes from the student")
  void theKeyBelongsToTheUniversity() {
    String key = storage().store(UPC, content("a"));

    assertThat(key).startsWith("UPC/").hasSize("UPC/".length() + 36);
    assertThat(Files.exists(folder.resolve(key))).isTrue();
  }

  @Test
  @DisplayName("two files get two keys, even with the same content")
  void twoFilesGetTwoKeys() {
    LocalDiskEvidenceStorageAdapter storage = storage();

    assertThat(storage.store(UPC, content("same"))).isNotEqualTo(storage.store(UPC, content("same")));
  }

  @Test
  @DisplayName("a key is not readable from another university")
  void aKeyIsNotReadableFromAnotherUniversity() {
    LocalDiskEvidenceStorageAdapter storage = storage();
    String key = storage.store(UPC, content("private"));

    assertThatThrownBy(() -> storage.open(UTEC, key)).isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> storage.delete(UTEC, key)).isInstanceOf(NoSuchElementException.class);
    assertThat(Files.exists(folder.resolve(key))).isTrue();
  }

  @Test
  @DisplayName("a key that climbs out of the university's folder is refused")
  void aKeyThatClimbsOutIsRefused() throws IOException {
    LocalDiskEvidenceStorageAdapter storage = storage();
    String key = storage.store(UTEC, content("another university's file"));
    String sneaky = "UPC/../" + key;

    assertThatThrownBy(() -> storage.open(UPC, sneaky)).isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> storage.delete(UPC, sneaky)).isInstanceOf(NoSuchElementException.class);
    assertThat(Files.exists(folder.resolve(key))).isTrue();
  }

  @Test
  @DisplayName("a key that points at the university's folder is refused and the folder survives")
  void aKeyThatPointsAtTheFolderIsRefused() {
    LocalDiskEvidenceStorageAdapter storage = storage();
    storage.store(UPC, content("x"));

    assertThatThrownBy(() -> storage.delete(UPC, "UPC/")).isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> storage.open(UPC, "UPC/")).isInstanceOf(NoSuchElementException.class);
    assertThat(Files.isDirectory(folder.resolve("UPC"))).isTrue();
  }

  @Test
  @DisplayName("a university code that cannot be a folder name is refused")
  void aUniversityCodeThatCannotBeAFolderNameIsRefused() {
    LocalDiskEvidenceStorageAdapter storage = storage();

    assertThatThrownBy(() -> storage.store("../UPC", content("a"))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage.store("UP C", content("a"))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage.store("", content("a"))).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a key with nothing stored under it is not found")
  void aKeyWithNothingStoredIsNotFound() {
    assertThatThrownBy(() -> storage().open(UPC, "UPC/" + "0".repeat(36)))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("deleting removes the file, and deleting twice is harmless")
  void deletingRemovesTheFile() {
    LocalDiskEvidenceStorageAdapter storage = storage();
    String key = storage.store(UPC, content("temporary"));

    storage.delete(UPC, key);
    storage.delete(UPC, key);

    assertThat(Files.exists(folder.resolve(key))).isFalse();
    assertThatThrownBy(() -> storage.open(UPC, key)).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("a stream that fails midway leaves no half file behind")
  void aFailingStreamLeavesNoHalfFile() {
    LocalDiskEvidenceStorageAdapter storage = storage();
    InputStream failing =
        new InputStream() {
          private int sent;

          @Override
          public int read() throws IOException {
            if (sent++ < 4) {
              return 'x';
            }
            throw new IOException("the connection dropped");
          }
        };

    assertThatThrownBy(() -> storage.store(UPC, failing)).isInstanceOf(EvidenceStorageException.class);

    try (var files = Files.walk(folder)) {
      assertThat(files.filter(Files::isRegularFile)).isEmpty();
    } catch (IOException failure) {
      throw new AssertionError(failure);
    }
  }
}
