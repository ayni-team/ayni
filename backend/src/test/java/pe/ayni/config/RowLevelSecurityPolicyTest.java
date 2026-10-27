package pe.ayni.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * TS01: a table with {@code tenant_id} cannot reach {@code develop} without its policy (ADR 0007).
 *
 * <p>It reads the catalogue of the database every migration built, so a table added by a later
 * migration is checked the moment it exists. Only the four tables the ADR names are exempt.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@Import(TenancyTestConfig.class)
class RowLevelSecurityPolicyTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = TenancyTestDatabase.INSTANCE;

  static final List<String> EXEMPT =
      List.of(
          "identity.tenants",
          "identity.platform_admins",
          "identity.access_links",
          "identity.user_sessions");

  @Autowired private JdbcTemplate jdbc;

  /** Every table of the modules' schemas that has a {@code tenant_id} column. */
  static final String TENANT_TABLES =
      """
      select n.nspname || '.' || k.relname
      from pg_class k
      join pg_namespace n on n.oid = k.relnamespace
      join pg_attribute a on a.attrelid = k.oid and a.attname = 'tenant_id' and not a.attisdropped
      where k.relkind in ('r', 'p')
        and n.nspname not in ('pg_catalog', 'information_schema', 'public')
      order by 1
      """;

  @Test
  @DisplayName("Every table with tenant_id but the four exempt ones has row level security and a policy")
  void everyTenantTableHasItsPolicy() {
    List<String> tables = jdbc.queryForList(TENANT_TABLES, String.class);
    // identity.tenants and identity.platform_admins have no tenant_id at all.
    assertThat(tables)
        .hasSizeGreaterThan(30)
        .contains("identity.access_links", "identity.user_sessions");

    List<String> unprotected =
        jdbc.queryForList(
            """
            select n.nspname || '.' || k.relname
            from pg_class k
            join pg_namespace n on n.oid = k.relnamespace
            where n.nspname || '.' || k.relname = any (?)
              and not (n.nspname || '.' || k.relname = any (?))
              and (not k.relrowsecurity
                   or not exists (select 1 from pg_policies p
                                  where p.schemaname = n.nspname and p.tablename = k.relname
                                    and p.qual like '%app.tenant_id%'
                                    and p.with_check like '%app.tenant_id%'))
            """,
            String.class,
            tables.toArray(String[]::new),
            EXEMPT.toArray(String[]::new));

    assertThat(unprotected).isEmpty();
  }

  @Test
  @DisplayName("The four exempt tables are left without row level security")
  void theExemptTablesHaveNone() {
    List<String> protectedExempt =
        jdbc.queryForList(
            """
            select n.nspname || '.' || k.relname
            from pg_class k
            join pg_namespace n on n.oid = k.relnamespace
            where n.nspname || '.' || k.relname = any (?) and k.relrowsecurity
            """,
            String.class,
            (Object) EXEMPT.toArray(String[]::new));

    assertThat(protectedExempt).isEmpty();
  }
}
