package pe.ayni.identity.application;

public interface AccessLinkUrlBuilder {

    String build(String tenantId, String rawToken);
}