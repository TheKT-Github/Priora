package com.gravitycanvas.repository;

import com.gravitycanvas.entity.GravitySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GravitySettingsRepository extends JpaRepository<GravitySettings, Long> {
}
