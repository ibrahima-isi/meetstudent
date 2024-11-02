package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchoolRepository extends JpaRepository<School, Integer> {

    List<School> findSchoolByName(String name);
}
