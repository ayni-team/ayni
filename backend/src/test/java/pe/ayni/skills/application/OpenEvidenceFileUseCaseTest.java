package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.application.OpenEvidenceFileUseCase.OpenedEvidence;
import pe.ayni.skills.domain.model.EvidenceFile;
import pe.ayni.skills.domain.model.NotACoordinator;
import pe.ayni.skills.infrastructure.EvidenceFileRepository;

/** US16: a coordinator opens a file of a submission, and only a file of that submission. */
class OpenEvidenceFileUseCaseTest {

  private static final String UPC = "UPC";
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-04T16:00:00Z");

  private final IdentityApi identity = mock(IdentityApi.class);
  private final EvidenceFileRepository evidenceFiles = mock(EvidenceFileRepository.class);
  private final EvidenceStoragePort storage = mock(EvidenceStoragePort.class);
  private final OpenEvidenceFileUseCase useCase =
      new OpenEvidenceFileUseCase(new CoordinatorGuard(identity), evidenceFiles, storage);

  private final UUID requestId = UUID.randomUUID();
  private final EvidenceFile file =
      new EvidenceFile(UUID.randomUUID(), UPC, requestId, "portfolio.pdf", "UPC/key-1", "application/pdf", 12, NOW);

  @BeforeEach
  void aCoordinatorAndAStoredFile() {
    when(identity.requireUser(COORDINATOR))
        .thenReturn(new UserView(COORDINATOR, UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "C", null, null, null));
    when(evidenceFiles.findByTenantIdAndId(UPC, file.getId())).thenReturn(Optional.of(file));
    when(storage.open(UPC, "UPC/key-1"))
        .thenReturn(new ByteArrayInputStream("%PDF-1.7 hello".getBytes(StandardCharsets.UTF_8)));
  }

  private OpenedEvidence open(UUID forRequest, UUID forFile) {
    Object[] result = new Object[1];
    TenantContext.runAs(UPC, () -> result[0] = useCase.execute(COORDINATOR, forRequest, forFile));
    return (OpenedEvidence) result[0];
  }

  @Test
  @DisplayName("a coordinator gets the file and its content")
  void aCoordinatorGetsTheFile() throws IOException {
    OpenedEvidence opened = open(requestId, file.getId());

    assertThat(opened.file()).isSameAs(file);
    try (InputStream content = opened.content()) {
      assertThat(new String(content.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("%PDF-1.7 hello");
    }
  }

  @Test
  @DisplayName("a file of another submission is not found, even with the right file identifier")
  void aFileOfAnotherSubmissionIsNotFound() {
    assertThatThrownBy(() -> open(UUID.randomUUID(), file.getId())).isInstanceOf(NoSuchElementException.class);

    verifyNoInteractions(storage);
  }

  @Test
  @DisplayName("a file that does not exist is not found")
  void aFileThatDoesNotExistIsNotFound() {
    UUID unknown = UUID.randomUUID();
    when(evidenceFiles.findByTenantIdAndId(UPC, unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> open(requestId, unknown)).isInstanceOf(NoSuchElementException.class);

    verifyNoInteractions(storage);
  }

  @Test
  @DisplayName("the content is asked for with the university of the request")
  void theContentIsAskedForWithTheUniversityOfTheRequest() {
    open(requestId, file.getId());

    verify(storage).open(UPC, "UPC/key-1");
  }

  @Test
  @DisplayName("a student cannot open the file, and nothing is read")
  void aStudentCannotOpenTheFile() {
    UUID student = UUID.randomUUID();
    when(identity.requireUser(student))
        .thenReturn(new UserView(student, UPC, UserRole.STUDENT, "s@upc.edu.pe", "U1", "S", "C", "1", null));

    assertThatThrownBy(
            () -> TenantContext.runAs(UPC, () -> useCase.execute(student, requestId, file.getId())))
        .isInstanceOf(NotACoordinator.class);

    verifyNoInteractions(evidenceFiles, storage);
  }
}
