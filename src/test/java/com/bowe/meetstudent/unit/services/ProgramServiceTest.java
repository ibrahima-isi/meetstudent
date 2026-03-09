package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.repositories.ProgramRepository;
import com.bowe.meetstudent.services.MediaService;
import com.bowe.meetstudent.services.ProgramService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ProgramServiceTest {

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private ProgramService programService;

    private Program program;

    @BeforeEach
    void setUp() {
        program = new Program();
        program.setId(1);
        program.setName("Computer Science");
        program.setCode("CS101");
        program.setDuration(4);
        program.setPhotoUrl("programs/test.jpg");
    }

    @Test
    void testSave() {
        Mockito.when(programRepository.save(any(Program.class))).thenReturn(program);
        
        Program saved = programService.save(program);
        
        assertNotNull(saved);
        assertEquals("Computer Science", saved.getName());
        Mockito.verify(programRepository, Mockito.times(1)).save(program);
    }

    @Test
    void testFindById() {
        Mockito.when(programRepository.findById(1)).thenReturn(Optional.of(program));
        
        Optional<Program> found = programService.findById(1);
        
        assertTrue(found.isPresent());
        assertEquals("CS101", found.get().getCode());
    }

    @Test
    void testFindAll() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<Program> page = new PageImpl<>(List.of(program));
        
        Mockito.when(programRepository.findAll(pageRequest)).thenReturn(page);
        
        Page<Program> result = programService.findAll(pageRequest);
        
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void testFindByName() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<Program> page = new PageImpl<>(List.of(program));
        
        Mockito.when(programRepository.findByNameContainingIgnoreCase("Computer", pageRequest)).thenReturn(page);
        
        Page<Program> result = programService.findByName("Computer", pageRequest);
        
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void testExists() {
        Mockito.when(programRepository.existsById(1)).thenReturn(true);
        assertTrue(programService.exists(1));
    }

    @Test
    void testDelete() {
        Mockito.when(programRepository.findById(1)).thenReturn(Optional.of(program));
        Mockito.doNothing().when(programRepository).deleteById(1);
        
        programService.delete(1);
        
        Mockito.verify(programRepository, Mockito.times(1)).findById(1);
        Mockito.verify(programRepository, Mockito.times(1)).deleteById(1);
        Mockito.verify(mediaService, Mockito.times(1)).deleteMediaByUrl("programs/test.jpg");
    }

    @Test
    void testPatch() {
        Program updates = Program.builder().name("New Name").photoUrl("programs/new.jpg").build();
        Mockito.when(programRepository.findById(1)).thenReturn(Optional.of(program));
        Mockito.when(programRepository.save(any(Program.class))).thenReturn(program);

        programService.patch(1, updates);

        assertEquals("New Name", program.getName());
        assertEquals("programs/new.jpg", program.getPhotoUrl());
        Mockito.verify(mediaService).deleteOldMediaIfChanged("programs/test.jpg", "programs/new.jpg");
    }
}
