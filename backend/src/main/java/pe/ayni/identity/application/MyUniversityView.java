package pe.ayni.identity.application;

import java.util.List;
import pe.ayni.identity.domain.model.Tenant;

/** The university of a signed in person: its identity, the domains it claims and its time zone. */
public record MyUniversityView(
        String code,
        String name,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        List<String> emailDomains,
        String timezone) {

    public static MyUniversityView from(Tenant tenant) {
        return new MyUniversityView(
                tenant.getCode(),
                tenant.getName(),
                tenant.getLogoUrl(),
                tenant.getPrimaryColor(),
                tenant.getSecondaryColor(),
                tenant.getEmailDomains(),
                tenant.getTimezone().getId());
    }
}
