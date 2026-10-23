package pe.ayni.booking.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.identity.IdentityApi;
import pe.ayni.skills.CatalogItemView;
import pe.ayni.skills.SkillsApi;

/**
 * The names a list of bookings shows next to the identifiers it keeps: the people, read from
 * identity, and the catalogue item, read from skills.
 *
 * <p>A list asks for all its names at once, one read per module however many bookings it has. A
 * person or an item the other module no longer knows is shown without a name rather than failing
 * the whole list.
 */
@Component
class BookingParties {

  private final IdentityApi identity;
  private final SkillsApi skills;

  BookingParties(IdentityApi identity, SkillsApi skills) {
    this.identity = identity;
    this.skills = skills;
  }

  /**
   * The names of the people and the items of these bookings. {@code personIdOf} says which person
   * of each one the list shows: the tutor in a student's list, the student in a tutor's.
   */
  Names forList(Collection<Booking> listed, Function<Booking, UUID> personIdOf) {
    return new Names(
        identity.namesOf(listed.stream().map(personIdOf).distinct().toList()),
        skills.itemsOf(listed.stream().map(Booking::getCatalogItemId).distinct().toList()));
  }

  record Names(Map<UUID, String> people, Map<UUID, CatalogItemView> items) {

    String personName(UUID userId) {
      return people.get(userId);
    }

    String itemName(UUID catalogItemId) {
      CatalogItemView item = items.get(catalogItemId);
      return item == null ? null : item.name();
    }
  }
}
