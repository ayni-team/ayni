package pe.ayni.booking.application;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.ayni.identity.IdentityApi;
import pe.ayni.skills.SkillsApi;

/**
 * The names a list of bookings shows next to the identifiers it keeps: the people, read from
 * identity, and the catalogue item, read from skills.
 *
 * <p>A list asks for each name once, however many of its bookings share it. A person or an item the
 * other module no longer knows is shown without a name rather than failing the whole list.
 */
@Component
class BookingParties {

  private final IdentityApi identity;
  private final SkillsApi skills;

  BookingParties(IdentityApi identity, SkillsApi skills) {
    this.identity = identity;
    this.skills = skills;
  }

  /** A lookup for one list: the names it reads are kept until the list is built. */
  Names forOneList() {
    return new Names();
  }

  final class Names {

    private final Map<UUID, String> people = new HashMap<>();
    private final Map<UUID, String> items = new HashMap<>();

    String personName(UUID userId) {
      return people.computeIfAbsent(
          userId,
          id -> {
            try {
              return identity.requireUser(id).fullName();
            } catch (NoSuchElementException unknown) {
              return null;
            }
          });
    }

    String itemName(UUID catalogItemId) {
      return items.computeIfAbsent(
          catalogItemId,
          id -> {
            try {
              return skills.requireItem(id).name();
            } catch (NoSuchElementException unknown) {
              return null;
            }
          });
    }
  }
}
