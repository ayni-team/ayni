package pe.ayni.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.ayni.identity.domain.model.EmailDomains;
import pe.ayni.identity.domain.model.IdentityRuleViolation;

class EmailDomainsTest {

    @Test
    @DisplayName("an address is trimmed and put in lower case")
    void anAddressIsNormalized() {
        assertThat(EmailDomains.requireAddress("  U202400001@UPC.edu.PE ")).isEqualTo("u202400001@upc.edu.pe");
    }

    @ParameterizedTest(name = "\"{0}\" is not an address")
    @ValueSource(strings = {"", "   ", "no-at-sign", "@upc.edu.pe", "someone@", "a@b@upc.edu.pe", "some one@upc.edu.pe"})
    void aMalformedAddressIsRefused(String value) {
        assertThatThrownBy(() -> EmailDomains.requireAddress(value)).isInstanceOf(IdentityRuleViolation.class);
    }

    @Test
    @DisplayName("a missing address is refused")
    void aMissingAddressIsRefused() {
        assertThatThrownBy(() -> EmailDomains.requireAddress(null)).isInstanceOf(IdentityRuleViolation.class);
    }

    @Test
    @DisplayName("an address longer than the standard allows is refused")
    void aTooLongAddressIsRefused() {
        String address = "a".repeat(250) + "@upc.pe";

        assertThatThrownBy(() -> EmailDomains.requireAddress(address)).isInstanceOf(IdentityRuleViolation.class);
    }

    @Test
    @DisplayName("domains are trimmed, in lower case and without repeats, in the order they came")
    void domainsAreNormalized() {
        assertThat(EmailDomains.normalize(List.of(" UPC.edu.pe", "upc.pe", "upc.EDU.pe")))
                .containsExactly("upc.edu.pe", "upc.pe");
    }

    @Test
    @DisplayName("a list without domains is refused")
    void aListWithoutDomainsIsRefused() {
        assertThatThrownBy(() -> EmailDomains.normalize(List.of()))
                .isInstanceOf(IdentityRuleViolation.class)
                .hasMessageContaining("At least one");
        assertThatThrownBy(() -> EmailDomains.normalize(null)).isInstanceOf(IdentityRuleViolation.class);
    }

    @ParameterizedTest(name = "\"{0}\" is not a domain")
    @ValueSource(strings = {"", "   ", "someone@upc.edu.pe", "upc edu.pe"})
    void aDomainThatIsNotOneIsRefused(String value) {
        assertThatThrownBy(() -> EmailDomains.normalize(List.of("upc.edu.pe", value)))
                .isInstanceOf(IdentityRuleViolation.class);
    }

    @Test
    @DisplayName("a missing domain in the list is refused")
    void aMissingDomainIsRefused() {
        assertThatThrownBy(() -> EmailDomains.normalize(Arrays.asList("upc.edu.pe", null)))
                .isInstanceOf(IdentityRuleViolation.class);
    }

    @Test
    @DisplayName("a lookup domain is trimmed and put in lower case")
    void aLookupDomainIsNormalized() {
        assertThat(EmailDomains.requireLookupDomain("  UPC.edu.PE ")).isEqualTo("upc.edu.pe");
    }

    @ParameterizedTest(name = "\"{0}\" is not a lookup domain")
    @ValueSource(strings = {"", "   ", "upc", "someone@upc.edu.pe", "upc edu.pe", ".upc.pe", "upc.pe.", "upc..pe"})
    void aLookupDomainThatDoesNotLookLikeOneIsRefused(String value) {
        assertThatThrownBy(() -> EmailDomains.requireLookupDomain(value))
                .isInstanceOf(IdentityRuleViolation.class);
    }

    @Test
    @DisplayName("a missing or too long lookup domain is refused")
    void aMissingOrTooLongLookupDomainIsRefused() {
        assertThatThrownBy(() -> EmailDomains.requireLookupDomain(null)).isInstanceOf(IdentityRuleViolation.class);
        assertThatThrownBy(() -> EmailDomains.requireLookupDomain("a".repeat(251) + ".pe"))
                .isInstanceOf(IdentityRuleViolation.class);
    }
}
