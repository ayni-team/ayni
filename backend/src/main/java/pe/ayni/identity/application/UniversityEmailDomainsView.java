package pe.ayni.identity.application;

import java.util.List;
import pe.ayni.identity.domain.model.Tenant;

/** The domains a university is recognised by, with nothing about its people. */
public record UniversityEmailDomainsView(String code, String name, List<String> emailDomains) {

    public static UniversityEmailDomainsView from(Tenant tenant) {
        return new UniversityEmailDomainsView(
                tenant.getCode(), tenant.getName(), tenant.getEmailDomains());
    }
}
