package pe.ayni.notifications;

import jakarta.mail.Multipart;
import jakarta.mail.Part;

/** Reads a received email whatever its shape: only text, or text and HTML as alternatives. */
public final class EmailParts {

  private EmailParts() {}

  public static String plainTextOf(Part email) throws Exception {
    return partOf(email, "text/plain");
  }

  public static String htmlOf(Part email) throws Exception {
    return partOf(email, "text/html");
  }

  private static String partOf(Part part, String type) throws Exception {
    if (part.isMimeType(type)) {
      return (String) part.getContent();
    }
    if (part.getContent() instanceof Multipart parts) {
      for (int i = 0; i < parts.getCount(); i++) {
        String found = partOf(parts.getBodyPart(i), type);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
