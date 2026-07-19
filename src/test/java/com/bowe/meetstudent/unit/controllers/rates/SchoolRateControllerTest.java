package com.bowe.meetstudent.unit.controllers.rates;

import com.bowe.meetstudent.controllers.rates.SchoolRateController;
import com.bowe.meetstudent.dto.SchoolRateDTO;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.rates.SchoolRate;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.SchoolRateService;
import com.bowe.meetstudent.services.SchoolService;
import com.bowe.meetstudent.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class SchoolRateControllerTest {

    @Mock
    private SchoolRateService service;

    @Mock
    private Mapper<SchoolRate, SchoolRateDTO> mapper;

    @Mock
    private SchoolService schoolService;

    @Mock
    private UserService userService;

    @InjectMocks
    private SchoolRateController controller;

    @Test
    void create_shouldUseAuthenticatedUser_whenUserIdIsAbsent() {
        UserPrincipal principal = principal(7, "ROLE_STUDENT");
        UserEntity authenticatedUser = UserEntity.builder().id(7).build();
        School school = School.builder().id(11).build();
        SchoolRate rate = new SchoolRate();
        SchoolRateDTO request = SchoolRateDTO.builder().note(4.0).schoolId(11).build();
        SchoolRateDTO response = SchoolRateDTO.builder().id(20).note(4.0).schoolId(11).userId(7).build();

        Mockito.when(mapper.toEntity(request)).thenReturn(rate);
        Mockito.when(schoolService.getSchoolById(11)).thenReturn(Optional.of(school));
        Mockito.when(userService.resolveAuthenticatedUser(7, null)).thenReturn(authenticatedUser);
        Mockito.when(service.save(rate)).thenReturn(rate);
        Mockito.when(mapper.toDTO(rate)).thenReturn(response);

        var result = controller.create(principal, request);

        assertEquals(201, result.getStatusCode().value());
        assertEquals(authenticatedUser, rate.getUserEntity());
        assertEquals(school, rate.getSchool());
    }

    @Test
    void create_shouldReject_whenRequestedUserDiffersFromPrincipal() {
        UserPrincipal principal = principal(7, "ROLE_STUDENT");
        School school = School.builder().id(11).build();
        SchoolRate rate = new SchoolRate();
        SchoolRateDTO request = SchoolRateDTO.builder().note(4.0).schoolId(11).userId(8).build();

        Mockito.when(mapper.toEntity(request)).thenReturn(rate);
        Mockito.when(schoolService.getSchoolById(11)).thenReturn(Optional.of(school));
        Mockito.when(userService.resolveAuthenticatedUser(7, 8))
                .thenThrow(new AccessDeniedException("Forbidden"));

        assertThrows(AccessDeniedException.class, () -> controller.create(principal, request));
        Mockito.verify(service, Mockito.never()).save(rate);
    }

    private UserPrincipal principal(Integer id, String role) {
        return UserPrincipal.builder()
                .id(id)
                .username("user@example.com")
                .authorities(List.of(new SimpleGrantedAuthority(role)))
                .build();
    }
}
