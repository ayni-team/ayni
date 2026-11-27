package pe.ayni.payments.interfaces.rest;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Readiness for the container's health check: UP once the database answers. Actuator would do the
 * same with a few megabytes more of classes and beans, which this service does not have to spare.
 * It needs no token and reads nothing about anybody.
 */
@RestController
class HealthController {

  private final JdbcTemplate jdbc;

  HealthController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/health")
  ResponseEntity<Map<String, String>> health() {
    try {
      jdbc.queryForObject("select 1", Integer.class);
      return ResponseEntity.ok(Map.of("status", "UP"));
    } catch (DataAccessException unavailable) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
    }
  }
}
