package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.BadgeDto;
import com.poshanforlife.api.dto.CreateBadgeRequest;
import com.poshanforlife.api.dto.UpdateBadgeRequest;
import com.poshanforlife.api.entity.Badge;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.BadgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** The small, admin-managed badge catalog. Deleting a badge cascades to any PatientBadge rows for it (DB ON DELETE CASCADE). */
@Service
@RequiredArgsConstructor
public class BadgeService {

    private final BadgeRepository badgeRepository;

    @Transactional(readOnly = true)
    public List<BadgeDto> list() {
        return badgeRepository.findAllByOrderByCreatedAtDesc().stream().map(BadgeService::toDto).toList();
    }

    @Transactional
    public BadgeDto create(CreateBadgeRequest request) {
        Badge badge = new Badge();
        badge.setName(request.name().trim());
        badge.setDescription(blankToNull(request.description()));
        badge.setIconKey(request.iconKey().trim());
        badge.setCriteriaType(request.criteriaType());
        badge.setCriteriaValue(request.criteriaValue());
        return toDto(badgeRepository.save(badge));
    }

    @Transactional
    public BadgeDto update(UUID id, UpdateBadgeRequest request) {
        Badge badge = find(id);
        if (request.name() != null) badge.setName(request.name().trim());
        if (request.description() != null) badge.setDescription(blankToNull(request.description()));
        if (request.iconKey() != null) badge.setIconKey(request.iconKey().trim());
        if (request.criteriaType() != null) badge.setCriteriaType(request.criteriaType());
        if (request.criteriaValue() != null) badge.setCriteriaValue(request.criteriaValue());
        return toDto(badge);
    }

    @Transactional
    public void delete(UUID id) {
        badgeRepository.delete(find(id));
    }

    private Badge find(UUID id) {
        return badgeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Badge", id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static BadgeDto toDto(Badge b) {
        return new BadgeDto(b.getId().toString(), b.getName(), b.getDescription(), b.getIconKey(),
                b.getCriteriaType(), b.getCriteriaValue(), b.getCreatedAt());
    }
}
