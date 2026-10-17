package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, Integer> {
}
