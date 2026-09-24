package pe.ayni.identity.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import pe.ayni.identity.application.AccessLinkUrlBuilder;

@Component
public class ConfiguredAccessLinkUrlBuilder implements AccessLinkUrlBuilder {

    private final String baseUrl;

    public ConfiguredAccessLinkUrlBuilder(
            @Value("${ayni.identity.access-link-base-url:http://localhost:5173/access/confirm}")
            String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public String build(String rawToken) {
        return UriComponentsBuilder
                .fromUriString(baseUrl)
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();
    }
}