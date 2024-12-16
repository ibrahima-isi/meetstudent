package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.School;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchoolRepository extends JpaRepository<School, Integer> {

    Page<School> findSchoolByName(String name, Pageable pageable);

    Page<School> findSchoolByAddress_City(String city, Pageable pageable);
}
