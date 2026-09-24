package pe.ayni.skills.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * A group catalog items are organised under: technology, university courses, languages, design.
 *
 * <p>Global, the same for every university. Nothing in this module's scope creates one yet; they
 * ship with Ayni and are seeded directly in the database.
 */
@Entity
@Table(schema = "skills", name = "categories")
public class Category {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", length = 80, nullable = false)
  private String name;

  @Column(name = "sort_order", nullable = false)
  private short sortOrder;

  protected Category() {
    // Required by JPA
  }

  public Category(UUID id, String name, short sortOrder) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.name = requireNonBlank(name);
    this.sortOrder = sortOrder;
  }

  private static String requireNonBlank(String name) {
    Objects.requireNonNull(name, "name must not be null");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    return name;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public short getSortOrder() {
    return sortOrder;
  }
}
