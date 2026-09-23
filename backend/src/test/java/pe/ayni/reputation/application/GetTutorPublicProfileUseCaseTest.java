package pe.ayni.reputation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserView;
import pe.ayni.skills.CatalogItemView;
import pe.ayni.skills.SkillsApi;

class GetTutorPublicProfileUseCaseTest {

    @Test
    void buildsPublicProfileWithEnabledSkills() {
        UUID tutorId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();

        IdentityApi identity = mock(IdentityApi.class);
        SkillsApi skills = mock(SkillsApi.class);
        UserView tutor = mock(UserView.class);
        CatalogItemView catalogItem = mock(CatalogItemView.class);

        when(identity.requireUser(tutorId)).thenReturn(tutor);

        when(tutor.id()).thenReturn(tutorId);
        when(tutor.fullName()).thenReturn("Ana Torres");
        when(tutor.career()).thenReturn("Software Engineering");
        when(tutor.currentTerm()).thenReturn("2026-2");
        when(tutor.photoUrl()).thenReturn("https://example.com/photo.jpg");

        when(skills.enabledSkillsOf(tutorId)).thenReturn(List.of(skillId));
        when(skills.requireItem(skillId)).thenReturn(catalogItem);

        when(catalogItem.id()).thenReturn(skillId);
        when(catalogItem.name()).thenReturn("Software Architecture");
        when(catalogItem.courseCode()).thenReturn("SI657");

        GetTutorPublicProfileUseCase useCase =
                new GetTutorPublicProfileUseCase(identity, skills);

        TutorPublicProfile profile = useCase.execute(tutorId);

        assertEquals(tutorId, profile.tutorId());
        assertEquals("Ana Torres", profile.fullName());
        assertEquals("Software Engineering", profile.career());
        assertEquals("2026-2", profile.currentTerm());
        assertEquals("https://example.com/photo.jpg", profile.photoUrl());

        assertEquals(1, profile.enabledSkills().size());
        assertEquals(skillId, profile.enabledSkills().getFirst().id());
        assertEquals("Software Architecture", profile.enabledSkills().getFirst().name());
        assertEquals("SI657", profile.enabledSkills().getFirst().courseCode());
    }
}