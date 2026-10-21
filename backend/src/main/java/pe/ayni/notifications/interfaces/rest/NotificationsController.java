package pe.ayni.notifications.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.notifications.application.ListMyNotificationsQuery;
import pe.ayni.notifications.application.MarkAllNotificationsReadUseCase;
import pe.ayni.notifications.application.MarkNotificationReadUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * The notices of the person who signed in, a student or a coordinator.
 *
 * <p>Every endpoint answers about the person of the session, read from {@link CurrentUser}: none
 * takes a recipient, so none can be pointed at somebody else's notices.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Validated
@Tag(name = "Notifications", description = "The notices of the person who signed in")
class NotificationsController {

  private final ListMyNotificationsQuery listMyNotifications;
  private final MarkNotificationReadUseCase markNotificationRead;
  private final MarkAllNotificationsReadUseCase markAllNotificationsRead;

  NotificationsController(
      ListMyNotificationsQuery listMyNotifications,
      MarkNotificationReadUseCase markNotificationRead,
      MarkAllNotificationsReadUseCase markAllNotificationsRead) {
    this.listMyNotifications = listMyNotifications;
    this.markNotificationRead = markNotificationRead;
    this.markAllNotificationsRead = markAllNotificationsRead;
  }

  @GetMapping
  @Operation(
      summary = "My notices, newest first",
      description =
          "The notices of the application for the person of the session, in their university. "
              + "A notice is listed even when its email did not arrive. The emailed access links "
              + "and presence codes are not notices and never appear. `unreadCount` counts every "
              + "unread notice, not only the ones in the page.")
  @ApiResponse(
      responseCode = "200",
      description = "One page of notices",
      content =
          @Content(
              schema = @Schema(implementation = NotificationsPage.class),
              examples =
                  @ExampleObject(
                      name = "A tutor who did not show up and a purchase",
                      value =
                          """
                          {
                            "items": [
                              {
                                "id": "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f",
                                "kind": "TUTOR_NO_SHOW",
                                "payload": {"sessionId": "d1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e5f"},
                                "createdAt": "2026-10-20T14:31:05Z",
                                "readAt": null
                              },
                              {
                                "id": "9f1c2d3e-4b5a-4c7d-8e9f-0a1b2c3d4e5f",
                                "kind": "PURCHASE_CONFIRMED",
                                "payload": {
                                  "purchaseId": "7a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
                                  "credits": "10",
                                  "occurredOn": "2026-10-19"
                                },
                                "createdAt": "2026-10-19T20:02:11Z",
                                "readAt": "2026-10-19T20:10:00Z"
                              }
                            ],
                            "page": 0,
                            "size": 20,
                            "total": 2,
                            "unreadCount": 1
                          }
                          """)))
  @ApiResponse(
      responseCode = "400",
      description = "A page below 0, a size outside 1 to 100, or a value that cannot be read",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  NotificationsPage list(
      @Parameter(description = "Only the notices not read yet") @RequestParam(defaultValue = "false")
          boolean unread,
      @Parameter(description = "Page number, starting at zero") @RequestParam(defaultValue = "0")
          @Min(0)
          int page,
      @Parameter(description = "Notices per page, at most 100") @RequestParam(defaultValue = "20")
          @Min(1)
          @Max(100)
          int size) {
    return NotificationsPage.of(listMyNotifications.execute(CurrentUser.require(), unread, page, size));
  }

  @PostMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Mark one of my notices as read",
      description =
          "Records that the person has seen the notice. Marking one that is already read changes "
              + "nothing and keeps the first date. A notice of somebody else, of another "
              + "university or that does not exist is not found.")
  @ApiResponse(responseCode = "204", description = "The notice is read")
  @ApiResponse(
      responseCode = "400",
      description = "The identifier is not a UUID",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The person has no such notice in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  void read(
      @Parameter(
              description = "Notice identifier",
              example = "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f")
          @PathVariable("id")
          UUID id) {
    markNotificationRead.execute(CurrentUser.require(), id);
  }

  @PostMapping("/read-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Mark all my notices as read",
      description =
          "Records that the person has seen every notice they had not read. The ones already read "
              + "keep their date, and having none unread is not an error.")
  @ApiResponse(responseCode = "204", description = "No notice is left unread")
  void readAll() {
    markAllNotificationsRead.execute(CurrentUser.require());
  }
}
