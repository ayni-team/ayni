package pe.ayni.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.application.GeneratedAccessToken;

class AccessTokenServiceTest {

    private final AccessTokenService tokens = new AccessTokenService();

    @Test
    @DisplayName("generated tokens are stored only through their SHA-256 hash")
    void generatedTokenContainsItsHash() {
        GeneratedAccessToken token = tokens.generate();

        assertThat(token.rawToken()).isNotBlank();
        assertThat(token.tokenHash()).hasSize(64);
        assertThat(token.tokenHash()).isEqualTo(tokens.hash(token.rawToken()));
        assertThat(token.tokenHash()).isNotEqualTo(token.rawToken());
    }

    @Test
    @DisplayName("two generated access tokens are different")
    void generatedTokensAreDifferent() {
        GeneratedAccessToken first = tokens.generate();
        GeneratedAccessToken second = tokens.generate();

        assertThat(first.rawToken()).isNotEqualTo(second.rawToken());
        assertThat(first.tokenHash()).isNotEqualTo(second.tokenHash());
    }

    @Test
    @DisplayName("hashing the same token produces the same value")
    void hashingIsDeterministic() {
        String token = "development-token";

        assertThat(tokens.hash(token))
                .isEqualTo(tokens.hash(token))
                .hasSize(64);
    }
}