package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.TagDTO;
import com.bowe.meetstudent.entities.Tag;
import com.bowe.meetstudent.mappers.implementations.TagMapper;
import com.bowe.meetstudent.services.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tags")
@io.swagger.v3.oas.annotations.tags.Tag(name = "07. Tags", description = "Endpoints for managing school classifications and categories independently")
public class TagController {

    private final TagService tagService;
    private final TagMapper tagMapper;

    @PostMapping
    @Operation(summary = "Create a new tag", description = "Adds a new classification tag to the system.")
    @ApiResponse(responseCode = "201", description = "Tag created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid tag data or name already exists")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TagDTO> create(@RequestBody TagDTO tagDTO) {
        com.bowe.meetstudent.entities.Tag tag = tagMapper.toEntity(tagDTO);
        com.bowe.meetstudent.entities.Tag saved = tagService.save(tag);
        return new ResponseEntity<>(tagMapper.toDTO(saved), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all tags", description = "Retrieves a list of all available classification tags.")
    @ApiResponse(responseCode = "200", description = "List of tags retrieved")
    public ResponseEntity<List<TagDTO>> getAll() {
        List<TagDTO> tags = tagService.findAll()
                .stream()
                .map(tagMapper::toDTO)
                .toList();
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a tag by ID", description = "Retrieves details of a specific tag using its unique ID.")
    @ApiResponse(responseCode = "200", description = "Tag found")
    @ApiResponse(responseCode = "404", description = "Tag not found")
    public ResponseEntity<TagDTO> getById(@PathVariable Integer id) {
        return tagService.findById(id)
                .map(tag -> ResponseEntity.ok(tagMapper.toDTO(tag)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/name/{name}")
    @Operation(summary = "Get a tag by name", description = "Retrieves details of a specific tag using its unique name.")
    @ApiResponse(responseCode = "200", description = "Tag found")
    @ApiResponse(responseCode = "404", description = "Tag not found")
    public ResponseEntity<TagDTO> getByName(@PathVariable String name) {
        return tagService.findByName(name)
                .map(tag -> ResponseEntity.ok(tagMapper.toDTO(tag)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a tag by ID", description = "Removes a tag from the system. Note: This may affect schools associated with this tag.")
    @ApiResponse(responseCode = "204", description = "Tag deleted successfully")
    @ApiResponse(responseCode = "404", description = "Tag not found")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        if (!tagService.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
