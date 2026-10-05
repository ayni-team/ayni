package pe.ayni.skills.application;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.EvidenceFile;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.domain.model.ValidationRequest;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.EvidenceFileRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.ValidationRequestRepository;

/**
 * US16, scenarios 1 and 3: a tutor presents evidence for a global tool, and it waits for a
 * coordinator of their university.
 *
 * <p>The first submission creates the skill, pending. After a rejection the same skill goes back to
 * pending with a new request, and the earlier one stays as it was, so the reason the student read
 * does not disappear when they try again.
 *
 * <p>Everything that can be refused is decided before a byte is stored, and what was stored is
 * taken back if a later step fails, so a refused submission leaves neither a row nor a file.
 */
@Service
public class SubmitEvidenceUseCase {

  private final CatalogItemRepository catalogItems;
  private final OfferedSkillRepository offeredSkills;
  private final ValidationRequestRepository requests;
  private final EvidenceFileRepository evidenceFiles;
  private final EvidenceStoragePort storage;
  private final EvidenceRules rules;
  private final Clock clock;

  SubmitEvidenceUseCase(
      CatalogItemRepository catalogItems,
      OfferedSkillRepository offeredSkills,
      ValidationRequestRepository requests,
      EvidenceFileRepository evidenceFiles,
      EvidenceStoragePort storage,
      EvidenceRules rules,
      Clock clock) {
    this.catalogItems = catalogItems;
    this.offeredSkills = offeredSkills;
    this.requests = requests;
    this.evidenceFiles = evidenceFiles;
    this.storage = storage;
    this.rules = rules;
    this.clock = clock;
  }

  /** A file the student attaches. The caller opens the stream and closes it afterwards. */
  public record EvidenceFileInput(
      String fileName, String contentType, long sizeBytes, InputStream content) {}

  /** What was received: the skill, the request that now waits, and its files. */
  public record Submission(
      OfferedSkill skill, ValidationRequest request, List<EvidenceFile> files) {}

  /**
   * @param note what the student wants the reviewer to know; may be {@code null}
   * @throws NoSuchElementException when the item does not exist or is not visible to this tenant
   * @throws SkillsStateConflict when the tutor's evidence for the tool already waits, or the tool
   *     is already enabled or withdrawn
   * @throws SkillsRuleViolation when the item is retired or a course, or the files or the note
   *     break the rules of a submission
   * @throws EvidenceStorageException when the files cannot be stored
   */
  @Transactional
  public Submission execute(
      UUID tutorId, UUID catalogItemId, String note, List<EvidenceFileInput> files) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");
    Objects.requireNonNull(files, "files must not be null");

    String tenantId = TenantContext.require();

    CatalogItem item =
        catalogItems
            .findByIdAndTenantVisibility(catalogItemId, tenantId)
            .orElseThrow(
                () -> new NoSuchElementException("catalog item %s not found".formatted(catalogItemId)));
    if (!item.isActive()) {
      throw new SkillsRuleViolation("this catalogue item is retired and can no longer be offered");
    }
    if (item.getScope() != CatalogScope.GLOBAL) {
      throw new SkillsRuleViolation(
          "a course is enabled by the academic record, not by evidence; offer it directly");
    }

    rules.check(
        files.stream()
            .map(file -> new EvidenceUpload(file.fileName(), file.contentType(), file.sizeBytes()))
            .toList());
    List<InputStream> streams = checkedStreams(files);

    Instant now = clock.instant();
    // Built before anything is stored: it is what refuses a note that is too long.
    UUID requestId = UUID.randomUUID();

    OfferedSkill skill = resolveSkill(tenantId, tutorId, catalogItemId, now);
    ValidationRequest request = ValidationRequest.submit(requestId, tenantId, skill.getId(), note, now);

    List<String> storedKeys = new ArrayList<>(files.size());
    try {
      List<EvidenceFile> saved = new ArrayList<>(files.size());
      requests.save(request);
      for (int i = 0; i < files.size(); i++) {
        EvidenceFileInput file = files.get(i);
        String key = storage.store(tenantId, streams.get(i));
        storedKeys.add(key);
        saved.add(
            evidenceFiles.save(
                new EvidenceFile(
                    UUID.randomUUID(),
                    tenantId,
                    requestId,
                    file.fileName(),
                    key,
                    file.contentType(),
                    file.sizeBytes(),
                    now)));
      }
      // Forced here so that a failure of the database happens while the files can still be taken
      // back, and not at the commit, after this method has returned.
      evidenceFiles.flush();
      return new Submission(skill, request, saved);
    } catch (RuntimeException failure) {
      discard(tenantId, storedKeys);
      throw failure;
    }
  }

  /** The tutor's skill for the tool, created pending or put back in the queue. */
  private OfferedSkill resolveSkill(String tenantId, UUID tutorId, UUID catalogItemId, Instant now) {
    Optional<OfferedSkill> existing =
        offeredSkills.lockByTenantIdAndTutorIdAndCatalogItemId(tenantId, tutorId, catalogItemId);
    if (existing.isEmpty()) {
      try {
        return offeredSkills.saveAndFlush(
            OfferedSkill.requestValidation(UUID.randomUUID(), tenantId, tutorId, catalogItemId, now));
      } catch (DataIntegrityViolationException raced) {
        // Another submission of the same tool created the row between the read and the insert.
        throw new SkillsStateConflict("this tool already has a submission being received");
      }
    }
    OfferedSkill skill = existing.get();
    switch (skill.getStatus()) {
      case REJECTED -> skill.resubmitEvidence(now);
      case PENDING -> throw new SkillsStateConflict("your evidence for this tool already waits for a review");
      case ENABLED -> throw new SkillsStateConflict("you already offer this tool");
      case WITHDRAWN ->
          throw new SkillsStateConflict(
              "you withdrew this tool and its evidence was already accepted, so it needs no new submission");
    }
    return skill;
  }

  /** Reads the first bytes of every file and checks them against the type it declares. */
  private List<InputStream> checkedStreams(List<EvidenceFileInput> files) {
    List<InputStream> streams = new ArrayList<>(files.size());
    for (EvidenceFileInput file : files) {
      BufferedInputStream stream = new BufferedInputStream(file.content());
      byte[] header;
      try {
        stream.mark(rules.signatureLength());
        header = stream.readNBytes(rules.signatureLength());
        stream.reset();
      } catch (IOException failure) {
        throw new EvidenceStorageException("could not read the uploaded file", failure);
      }
      if (!rules.matchesDeclaredType(file.contentType(), header)) {
        throw new SkillsRuleViolation(
            "the file %s is not what its type says it is".formatted(file.fileName()));
      }
      streams.add(stream);
    }
    return streams;
  }

  private void discard(String tenantId, List<String> storedKeys) {
    for (String key : storedKeys) {
      try {
        storage.delete(tenantId, key);
      } catch (RuntimeException ignored) {
        // The submission already failed and that is the error worth reporting; an orphan file is
        // harmless, because no row points at it.
      }
    }
  }
}
