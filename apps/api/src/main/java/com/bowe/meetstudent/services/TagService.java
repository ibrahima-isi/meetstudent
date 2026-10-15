package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Tag;
import com.bowe.meetstudent.exceptions.ConflictException;
import com.bowe.meetstudent.repositories.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional
    public Tag save(Tag tag) {
        if (tag.getName() != null && tagRepository.existsByNameIgnoreCase(tag.getName())) {
            throw new ConflictException("A tag with this name already exists");
        }
        return tagRepository.save(tag);
    }

    public List<Tag> findAll() {
        return tagRepository.findAll();
    }

    public Optional<Tag> findById(Integer id) {
        return tagRepository.findById(id);
    }

    public Optional<Tag> findByName(String name) {
        String safeName = (name == null) ? "" : name;
        return tagRepository.findByNameIgnoreCase(safeName);
    }

    @Transactional
    public Tag findOrCreate(String name) {
        return tagRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> tagRepository.save(Tag.builder().name(name.toUpperCase()).build()));
    }

    @Transactional
    public void delete(Integer id) {
        tagRepository.findById(id).ifPresent(tag -> {
            if (tag.getSchools() != null && !tag.getSchools().isEmpty()) {
                throw new ConflictException("This tag is still used by schools");
            }
            tagRepository.delete(tag);
        });
    }
}
