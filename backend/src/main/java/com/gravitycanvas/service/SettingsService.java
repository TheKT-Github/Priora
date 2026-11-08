package com.gravitycanvas.service;

import com.gravitycanvas.dto.GravitySettingsDto;
import com.gravitycanvas.entity.GravitySettings;
import com.gravitycanvas.repository.GravitySettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing Manager Customizable Gravity Settings & Ticket Approval Policy.
 */
@Service
public class SettingsService {

    private final GravitySettingsRepository settingsRepository;

    public SettingsService(GravitySettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    /**
     * Gets the active manager settings (or creates default if empty).
     */
    @Transactional(readOnly = true)
    public GravitySettings getSettings() {
        return settingsRepository.findAll().stream()
            .findFirst()
            .orElseGet(() -> settingsRepository.save(new GravitySettings(null, true, true, true, true, true)));
    }

    /**
     * Converts active settings to DTO.
     */
    @Transactional(readOnly = true)
    public GravitySettingsDto getSettingsDto() {
        GravitySettings s = getSettings();
        return new GravitySettingsDto(
            s.isEnableDependencyScore(),
            s.isEnableDeadlineProximityScore(),
            s.isEnableStagnationScore(),
            s.isEnableWorkloadDensityScore(),
            s.isRequireApprovalForDevTasks(),
            s.isEnableCustomFactor(),
            s.getCustomFactorName(),
            s.getCustomFactorPoints()
        );
    }

    /**
     * Updates manager settings checkboxes (Factor toggles, custom factor, and approval policy).
     */
    @Transactional
    public GravitySettingsDto updateSettings(GravitySettingsDto dto) {
        GravitySettings s = getSettings();
        s.setEnableDependencyScore(dto.isEnableDependencyScore());
        s.setEnableDeadlineProximityScore(dto.isEnableDeadlineProximityScore());
        s.setEnableStagnationScore(dto.isEnableStagnationScore());
        s.setEnableWorkloadDensityScore(dto.isEnableWorkloadDensityScore());
        s.setRequireApprovalForDevTasks(dto.isRequireApprovalForDevTasks());
        s.setEnableCustomFactor(dto.isEnableCustomFactor());
        s.setCustomFactorName(dto.getCustomFactorName() != null ? dto.getCustomFactorName().trim() : "Custom Factor");
        s.setCustomFactorPoints(dto.getCustomFactorPoints() != null ? dto.getCustomFactorPoints() : 0.0);

        GravitySettings saved = settingsRepository.save(s);
        return new GravitySettingsDto(
            saved.isEnableDependencyScore(),
            saved.isEnableDeadlineProximityScore(),
            saved.isEnableStagnationScore(),
            saved.isEnableWorkloadDensityScore(),
            saved.isRequireApprovalForDevTasks(),
            saved.isEnableCustomFactor(),
            saved.getCustomFactorName(),
            saved.getCustomFactorPoints()
        );
    }
}
