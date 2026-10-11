package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.SiteSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SiteSettingsRepository extends JpaRepository<SiteSettings, Integer> {
}
