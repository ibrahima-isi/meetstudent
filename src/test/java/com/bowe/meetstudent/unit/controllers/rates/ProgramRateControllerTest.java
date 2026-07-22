package com.bowe.meetstudent.unit.controllers.rates;

import com.bowe.meetstudent.controllers.rates.ProgramRateController;
import com.bowe.meetstudent.dto.ProgramRateDTO;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.rates.ProgramRate;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.ProgramRateService;
import com.bowe.meetstudent.services.ProgramService;
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
class ProgramRateControllerTest {

    @Mock
    private ProgramRateService service;

    @Mock
    private Mapper<ProgramRate, ProgramRateDTO> mapper;

    @Mock
    private ProgramService programService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ProgramRateController controller;

    @Test
    void create_shouldUseAuthenticatedUser_whenUserIdIsAbsent() {
        UserPrincipal principal = principal(7, "ROLE_EXPERT");
        UserEntity authenticatedUser = UserEntity.builder().id(7).build();
        Program program = Program.builder().id(11).build();
        ProgramRate rate = new ProgramRate();
        ProgramRateDTO request = ProgramRateDTO.builder().note(4.0).programId(11).build();
        ProgramRateDTO response = ProgramRateDTO.builder().id(20).note(4.0).programId(11).userId(7).build();

        Mockito.when(mapper.toEntity(request)).thenReturn(rate);
        Mockito.when(programService.findById(11)).thenReturn(Optional.of(program));
        Mockito.when(userService.resolveAuthenticatedUser(7, null)).thenReturn(authenticatedUser);
        Mockito.when(service.save(rate)).thenReturn(rate);
        Mockito.when(mapper.toDTO(rate)).thenReturn(response);

        var result = controller.create(principal, request);

        assertEquals(201, result.getStatusCode().value());
        assertEquals(authenticatedUser, rate.getUserEntity());
        assertEquals(program, rate.getProgram());
    }

    @Test
    void create_shouldReject_whenRequestedUserDiffersFromPrincipal() {
        UserPrincipal principal = principal(7, "ROLE_EXPERT");
        Program program = Program.builder().id(11).build();
        ProgramRate rate = new ProgramRate();
        ProgramRateDTO request = ProgramRateDTO.builder().note(4.0).programId(11).userId(8).build();

        Mockito.when(mapper.toEntity(request)).thenReturn(rate);
        Mockito.when(programService.findById(11)).thenReturn(Optional.of(program));
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
