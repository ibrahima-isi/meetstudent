package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.ContactMessage;
import com.bowe.meetstudent.exceptions.ResourceNotFoundException;
import com.bowe.meetstudent.repositories.ContactMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMessageService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ContactMessageRepository repository;

    /** Fixed order (newest first, id desc as tie-break): any requested sort is ignored, the size is capped. */
    @Transactional(readOnly = true)
    public Page<ContactMessage> list(Pageable requested) {
        var size = Math.min(requested.getPageSize(), MAX_PAGE_SIZE);
        return repository.findAll(PageRequest.of(requested.getPageNumber(), size, NEWEST_FIRST));
    }

    @Transactional
    public void delete(int id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Message not found");
        }
        repository.deleteById(id);
    }
}
