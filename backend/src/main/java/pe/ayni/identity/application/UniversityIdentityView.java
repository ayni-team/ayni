package pe.ayni.identity.application;

import pe.ayni.identity.domain.model.Tenant;

public record UniversityIdentityView(
        String code,
        String name,
        String logoUrl,
        String primaryColor,
        String secondaryColor) {

    public static UniversityIdentityView from(
            Tenant tenant) {

        return new UniversityIdentityView(
                tenant.getCode(),
                tenant.getName(),
                tenant.getLogoUrl(),
                tenant.getPrimaryColor(),
                tenant.getSecondaryColor());
    }
}