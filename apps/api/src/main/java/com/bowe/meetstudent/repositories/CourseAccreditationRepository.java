package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.CourseAccreditation;
import com.bowe.meetstudent.entities.embedded.CourseAccreditationId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface CourseAccreditationRepository extends JpaRepository<CourseAccreditation, CourseAccreditationId> {
    Page<CourseAccreditation> findByCourseId(Integer courseId, Pageable pageable);
}
