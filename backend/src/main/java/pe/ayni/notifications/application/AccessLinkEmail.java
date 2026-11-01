package pe.ayni.notifications.application;

import java.time.Duration;
import java.time.Instant;
import org.springframework.web.util.HtmlUtils;
import pe.ayni.shared.events.AccessRequested;

/**
 * What the email carrying an access link says.
 *
 * <p>In Spanish, like everything else the student reads in the web application. It says how long
 * the link lasts, that it works once, and what to do if the student did not ask for it: the three
 * things somebody receiving a key by email needs to know.
 *
 * <p>It leaves as HTML with the same words as plain text behind it. The logo is written in text and
 * not as an image: Gmail does not show SVG and most clients hold remote images back, and the first
 * thing the reader sees should not be a blank box. The link stays visible under the button for a
 * client that does not make the button work.
 */
final class AccessLinkEmail {

  private AccessLinkEmail() {}

  /** @param universityName the university to name in the email, or {@code null} to name none */
  static Email of(AccessRequested request, String universityName) {
    long minutes = minutesUntil(request.occurredOn(), request.expiresAt());
    return switch (request.purpose()) {
      case "ACTIVATION" ->
          email(
              request,
              universityName,
              minutes,
              "Activa tu cuenta de Ayni",
              "Usa este enlace para activar tu cuenta de Ayni:",
              "Pulsa el botón para activar tu cuenta de Ayni.",
              "Activar mi cuenta");
      case "COORDINATOR_INVITE" ->
          email(
              request,
              universityName,
              minutes,
              "Tu invitación como coordinador de Ayni",
              "Te invitaron a coordinar Ayni en tu universidad. Usa este enlace para aceptar:",
              "Te invitaron a coordinar Ayni en tu universidad. Pulsa el botón para aceptar.",
              "Aceptar la invitación");
      default ->
          email(
              request,
              universityName,
              minutes,
              "Tu enlace para ingresar a Ayni",
              "Usa este enlace para ingresar a Ayni:",
              "Pulsa el botón para ingresar a Ayni.",
              "Ingresar a Ayni");
    };
  }

  private static Email email(
      AccessRequested request,
      String universityName,
      long minutes,
      String subject,
      String opening,
      String lead,
      String action) {
    return new Email(
        request.email(),
        subject,
        body(opening, request, minutes),
        html(subject, lead, action, request, universityName, minutes));
  }

  private static String body(String opening, AccessRequested request, long minutes) {
    return """
        Hola:

        %s

        %s

        El enlace funciona una sola vez y vence en %s. Si no lo pediste, ignora este correo: \
        nadie puede entrar a tu cuenta sin él.

        Ayni, banco de tiempo académico
        """
        .formatted(opening, request.accessLink(), duration(minutes));
  }

  private static String html(
      String subject,
      String lead,
      String action,
      AccessRequested request,
      String universityName,
      long minutes) {
    String link = escaped(request.accessLink());
    String university =
        universityName == null
            ? ""
            : """
              <p style="margin:6px 0 0;font-size:14px;color:#5F6368;">%s</p>
            """
                .formatted(escaped(universityName));
    return """
        <!doctype html>
        <html lang="es">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>%s</title>
        </head>
        <body style="margin:0;padding:0;background-color:#F4F4F2;">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" \
        style="background-color:#F4F4F2;">
        <tr><td align="center" style="padding:32px 16px;">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" \
        style="max-width:520px;background-color:#FFFFFF;border-radius:12px;\
        font-family:Arial,Helvetica,sans-serif;color:#111111;">
        <tr><td style="padding:32px 32px 20px;border-bottom:1px solid #E7E7E4;">
          <p style="margin:0;font-size:30px;line-height:32px;font-weight:bold;">\
        <span style="color:#FFC700;">&#9679;</span>ayni</p>
        %s</td></tr>
        <tr><td style="padding:28px 32px 32px;">
          <h1 style="margin:0 0 12px;font-size:22px;line-height:28px;">%s</h1>
          <p style="margin:0 0 24px;font-size:16px;line-height:24px;">Hola. %s</p>
          <table role="presentation" cellpadding="0" cellspacing="0"><tr>
          <td style="border-radius:12px;background-color:#FFC700;">
          <a href="%s" style="display:inline-block;padding:14px 28px;font-size:16px;\
        font-weight:bold;color:#111111;text-decoration:none;">%s</a>
          </td></tr></table>
          <p style="margin:24px 0 0;font-size:14px;line-height:22px;color:#5F6368;">\
        El enlace funciona una sola vez y vence en %s. Si no lo pediste, ignora este correo: \
        nadie puede entrar a tu cuenta sin él.</p>
          <p style="margin:16px 0 0;font-size:13px;line-height:20px;color:#5F6368;">\
        Si el botón no funciona, copia este enlace en tu navegador:<br>\
        <a href="%s" style="color:#5F6368;word-break:break-all;">%s</a></p>
        </td></tr>
        <tr><td style="padding:16px 32px;background-color:#111111;border-radius:0 0 12px 12px;\
        font-size:13px;color:#FFFFFF;">Ayni, banco de tiempo académico</td></tr>
        </table>
        </td></tr>
        </table>
        </body>
        </html>
        """
        .formatted(
            escaped(subject),
            university,
            escaped(subject),
            escaped(lead),
            link,
            escaped(action),
            duration(minutes),
            link,
            link);
  }

  private static String escaped(String text) {
    return HtmlUtils.htmlEscape(text, "UTF-8");
  }

  private static String duration(long minutes) {
    return minutes + (minutes == 1 ? " minuto" : " minutos");
  }

  /** Whole minutes, rounded up: a link that lasts nine and a half minutes is not "9 minutes". */
  private static long minutesUntil(Instant from, Instant until) {
    long seconds = Math.max(Duration.between(from, until).toSeconds(), 0);
    return Math.max((seconds + 59) / 60, 1);
  }
}
