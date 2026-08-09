package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.Tag;
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
        tagRepository.deleteById(id);
    }
}
