package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.Accreditation;
import com.bowe.meetstudent.repositories.AccreditationRepository;
import com.bowe.meetstudent.services.AccreditationService;
import com.bowe.meetstudent.services.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AccreditationServiceTest {

    @Mock
    private AccreditationRepository accreditationRepository;

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private AccreditationService accreditationService;

    private Accreditation accreditation;

    @BeforeEach
    void setUp() {
        accreditation = new Accreditation();
        accreditation.setId(1);
        accreditation.setName("AACSB");
        accreditation.setCode("AACSB");
    }

    @Test
    void testSave() {
        Mockito.when(accreditationRepository.save(any(Accreditation.class))).thenReturn(accreditation);
        Accreditation saved = accreditationService.save(accreditation);
        assertNotNull(saved);
        assertEquals("AACSB", saved.getName());
    }

    @Test
    void testFindById() {
        Mockito.when(accreditationRepository.findById(1)).thenReturn(Optional.of(accreditation));
        Optional<Accreditation> found = accreditationService.findById(1);
        assertTrue(found.isPresent());
        assertEquals("AACSB", found.get().getName());
    }

    @Test
    void testDelete() {
        Mockito.doNothing().when(accreditationRepository).deleteById(1);
        accreditationService.delete(1);
        Mockito.verify(accreditationRepository, Mockito.times(1)).deleteById(1);
    }
}
