package pe.ayni.shared.demo;

import java.util.List;
import java.util.UUID;

/**
 * The people of the demonstration, with the identifiers and the addresses of the academic
 * documents ({@code backend/academic-directory}).
 *
 * <p>Every one of them is a student of a document: identity activates them and takes them as far
 * into the onboarding as {@link Step} says, skills finishes the onboarding of those that end it,
 * wallet gives them their credits, booking their hours and their tutorings. The identifiers are
 * fixed so that each module finds the same person, a restart finds them already there, and requests
 * saved in Swagger keep working. The table of who shows what is in {@code docs/demo.md}.
 *
 * <p>Every other student of the documents is left without an account, to activate it live.
 */
public final class DemoCast {

  /** How far into the onboarding a person starts. */
  public enum Step {
    PROFILE,
    ACADEMIC_RECORD,
    LEARNING_INTERESTS,
    COMPLETED
  }

  /** One person of the demonstration. */
  public record Person(String university, UUID id, String email, String fullName, Step step) {}

  public static final String UPC = "UPC";
  public static final String USIL = "USIL";

  // UPC. Ana and Bruno are the two people the demonstration started with.

  public static final Person ANA =
      upc("11111111-1111-4111-8111-111111111111", "u202400001", "Ana Torres", Step.PROFILE);
  public static final Person BRUNO =
      upc("22222222-2222-4222-8222-222222222222", "u202300002", "Bruno Salas", Step.COMPLETED);

  public static final Person MATEO_CARDENAS =
      upc("d1000000-0000-4000-8000-000000000001", "u202100026", "Mateo Cárdenas", Step.COMPLETED);
  public static final Person DANIELA_SALAS =
      upc("d1000000-0000-4000-8000-000000000002", "u202200018", "Daniela Salas", Step.COMPLETED);
  public static final Person ISABELLA_TAPIA =
      upc("d1000000-0000-4000-8000-000000000003", "u202300027", "Isabella Tapia", Step.COMPLETED);

  public static final Person ANDRES_ZEGARRA =
      upc("d1000000-0000-4000-8000-000000000011", "u202200019", "Andrés Zegarra", Step.COMPLETED);
  public static final Person ANA_FLORES =
      upc("d1000000-0000-4000-8000-000000000012", "u202300012", "Ana Flores", Step.COMPLETED);
  public static final Person JULIAN_ARCE =
      upc("d1000000-0000-4000-8000-000000000013", "u202300024", "Julián Arce", Step.COMPLETED);
  public static final Person VALERIA_MEDINA =
      upc("d1000000-0000-4000-8000-000000000014", "u202300014", "Valeria Medina", Step.COMPLETED);
  public static final Person MILAGROS_CASTILLO =
      upc("d1000000-0000-4000-8000-000000000015", "u202300033", "Milagros Castillo", Step.COMPLETED);
  public static final Person ALEJANDRA_CHAVEZ =
      upc("d1000000-0000-4000-8000-000000000016", "u202400031", "Alejandra Chávez", Step.COMPLETED);

  public static final Person DANIELA_CORNEJO =
      upc("d1000000-0000-4000-8000-000000000021", "u202400028", "Daniela Cornejo",
          Step.ACADEMIC_RECORD);
  public static final Person PAULA_PALOMINO =
      upc("d1000000-0000-4000-8000-000000000022", "u202300016", "Paula Palomino",
          Step.LEARNING_INTERESTS);

  // USIL. Lucía and Mateo are the two people the demonstration started with.

  public static final Person LUCIA =
      usil("66666666-6666-4666-8666-666666666666", "lucia.paredes", "Lucía Paredes", Step.PROFILE);
  public static final Person MATEO =
      usil("77777777-7777-4777-8777-777777777777", "mateo.rios", "Mateo Ríos", Step.COMPLETED);

  public static final Person JOAQUIN_MEDINA =
      usil("d2000000-0000-4000-8000-000000000001", "joaquin.medina", "Joaquín Medina",
          Step.COMPLETED);
  public static final Person DANIELA_TAPIA =
      usil("d2000000-0000-4000-8000-000000000002", "daniela.tapia", "Daniela Tapia", Step.COMPLETED);
  public static final Person PIERO_DELGADO =
      usil("d2000000-0000-4000-8000-000000000003", "piero.delgado", "Piero Delgado", Step.COMPLETED);

  public static final Person BRUNO_CASTILLO =
      usil("d2000000-0000-4000-8000-000000000011", "bruno.castillo", "Bruno Castillo",
          Step.COMPLETED);
  public static final Person SEBASTIAN_GUTIERREZ =
      usil("d2000000-0000-4000-8000-000000000012", "sebastian.gutierrez", "Sebastián Gutiérrez",
          Step.COMPLETED);
  public static final Person LUCIA_SALAZAR =
      usil("d2000000-0000-4000-8000-000000000013", "lucia.salazar", "Lucía Salazar", Step.COMPLETED);
  public static final Person DIEGO_ESPINOZA =
      usil("d2000000-0000-4000-8000-000000000014", "diego.espinoza", "Diego Espinoza",
          Step.COMPLETED);
  public static final Person ROCIO_PALOMINO =
      usil("d2000000-0000-4000-8000-000000000015", "rocio.palomino", "Rocío Palomino",
          Step.COMPLETED);
  public static final Person GABRIEL_NAVARRO =
      usil("d2000000-0000-4000-8000-000000000016", "gabriel.navarro", "Gabriel Navarro",
          Step.COMPLETED);

  public static final Person BRUNO_VARGAS =
      usil("d2000000-0000-4000-8000-000000000021", "bruno.vargas", "Bruno Vargas",
          Step.ACADEMIC_RECORD);
  public static final Person ALEJANDRA_GUTIERREZ =
      usil("d2000000-0000-4000-8000-000000000022", "alejandra.gutierrez", "Alejandra Gutiérrez",
          Step.LEARNING_INTERESTS);

  /** The coordinators {@code DemoCoordinatorData} creates under the {@code demo} profile. */
  public static final UUID UPC_COORDINATOR = UUID.fromString("44444444-4444-4444-8444-444444444444");

  public static final UUID USIL_COORDINATOR =
      UUID.fromString("88888888-8888-4888-8888-888888888888");

  /** Every student of the demonstration, in the order identity activates them. */
  public static final List<Person> STUDENTS =
      List.of(
          ANA, BRUNO, MATEO_CARDENAS, DANIELA_SALAS, ISABELLA_TAPIA, ANDRES_ZEGARRA, ANA_FLORES,
          JULIAN_ARCE, VALERIA_MEDINA, MILAGROS_CASTILLO, ALEJANDRA_CHAVEZ, DANIELA_CORNEJO,
          PAULA_PALOMINO,
          LUCIA, MATEO, JOAQUIN_MEDINA, DANIELA_TAPIA, PIERO_DELGADO, BRUNO_CASTILLO,
          SEBASTIAN_GUTIERREZ, LUCIA_SALAZAR, DIEGO_ESPINOZA, ROCIO_PALOMINO, GABRIEL_NAVARRO,
          BRUNO_VARGAS, ALEJANDRA_GUTIERREZ);

  private DemoCast() {}

  private static Person upc(String id, String code, String fullName, Step step) {
    return new Person(UPC, UUID.fromString(id), code + "@upc.edu.pe", fullName, step);
  }

  private static Person usil(String id, String user, String fullName, Step step) {
    return new Person(USIL, UUID.fromString(id), user + "@usil.edu.pe", fullName, step);
  }
}
