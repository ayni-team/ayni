package pe.ayni.identity.domain.model;

import java.util.List;
import java.util.Locale;

/**
 * The shape of an institutional email address and of the domains a university claims.
 *
 * <p>Both are normalized the same way everywhere: trimmed and in lower case, because a domain is
 * compared as text and the address of a person is written in any case.
 */
public final class EmailDomains {

    private static final int MAXIMUM_ADDRESS_LENGTH = 254;
    private static final int MAXIMUM_DOMAIN_LENGTH = 253;

    private EmailDomains() {}

    /**
     * The address in the form it is compared, or a refusal when it cannot be an address: it has
     * exactly one at sign with something on each side, and no whitespace.
     */
    public static String requireAddress(String email) {
        if (email == null || email.isBlank()) {
            throw new IdentityRuleViolation("Email must not be blank");
        }

        String address = email.trim().toLowerCase(Locale.ROOT);
        int separator = address.indexOf('@');

        if (address.length() > MAXIMUM_ADDRESS_LENGTH
                || address.chars().anyMatch(Character::isWhitespace)
                || separator <= 0
                || separator != address.lastIndexOf('@')
                || separator == address.length() - 1) {
            throw new IdentityRuleViolation("Email is not valid");
        }

        return address;
    }

    /**
     * The domain somebody looks a university up by, in the form it is compared, or a refusal when
     * it cannot be one: it is a name with at least one dot between labels, with no at sign and no
     * whitespace. Unlike the domains a university claims, which only have to be names, this one
     * comes from outside and has to look like what it is.
     */
    public static String requireLookupDomain(String value) {
        if (value == null || value.isBlank()) {
            throw new IdentityRuleViolation("Domain must not be blank");
        }

        String domain = value.trim().toLowerCase(Locale.ROOT);

        if (domain.length() > MAXIMUM_DOMAIN_LENGTH
                || domain.indexOf('@') >= 0
                || domain.chars().anyMatch(Character::isWhitespace)
                || domain.indexOf('.') < 0
                || domain.startsWith(".")
                || domain.endsWith(".")
                || domain.contains("..")) {
            throw new IdentityRuleViolation("Domain is not valid");
        }

        return domain;
    }

    /**
     * The domains a university would claim: at least one, each trimmed and in lower case, without
     * repeats and in the order they came. A domain is a name, so it has no at sign and no
     * whitespace.
     */
    public static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IdentityRuleViolation(
                    "At least one institutional email domain is required");
        }

        return values.stream()
                .map(EmailDomains::requireDomain)
                .distinct()
                .toList();
    }

    private static String requireDomain(String value) {
        if (value == null || value.isBlank()) {
            throw new IdentityRuleViolation("Email domain must not be blank");
        }

        String domain = value.trim().toLowerCase(Locale.ROOT);

        if (domain.indexOf('@') >= 0 || domain.chars().anyMatch(Character::isWhitespace)) {
            throw new IdentityRuleViolation("Email domain is not valid");
        }

        return domain;
    }
}
