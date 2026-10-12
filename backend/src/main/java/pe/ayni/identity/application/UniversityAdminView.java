package pe.ayni.identity.application;

import java.math.BigDecimal;
import java.util.List;

public record UniversityAdminView(
        String code,
        String name,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        List<String> emailDomains,
        BigDecimal minimumTeachingGrade,
        String timezone,
        boolean active,
        long studentCount,
        long coordinatorCount) {}