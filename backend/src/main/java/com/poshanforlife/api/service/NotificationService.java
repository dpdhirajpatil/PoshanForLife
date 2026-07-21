package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.NotificationDto;
import com.poshanforlife.api.dto.NotificationListResponseDto;
import com.poshanforlife.api.entity.Notification;
import com.poshanforlife.api.entity.NotificationPrefs;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.repository.NotificationRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The single place every feature creates a Notification through — never
 * construct the entity directly elsewhere, or notificationPrefs gating gets
 * silently bypassed. LEAD_FOLLOWUP has no dedicated pref field on
 * {@link NotificationPrefs} (only inbodyReport/patientAssigned/
 * processingErrors/systemAnnouncements exist) so it always sends; every other
 * known type maps to one of those four toggles.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_LIMIT = 50;

    private final NotificationRepository notificationRepository;

    /**
     * Creates a notification for {@code recipient}, unless their prefs opt out
     * of this type's category. relatedEntityType/Id may both be null when the
     * notification has no deep-link target.
     */
    @Transactional
    public void create(User recipient, String type, String title, String message,
                       String relatedEntityType, UUID relatedEntityId) {
        if (!isAllowed(recipient.getNotificationPrefs(), type)) {
            return;
        }
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRelatedEntityType(relatedEntityType);
        notification.setRelatedEntityId(relatedEntityId);
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public NotificationListResponseDto list(AuthenticatedUser caller, int limit, boolean unreadOnly) {
        UUID userId = UUID.fromString(caller.id());
        Pageable page = PageRequest.of(0, Math.min(limit, MAX_LIMIT));
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, page)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, page);
        long unreadCount = notificationRepository.countByUserIdAndReadFalse(userId);
        return new NotificationListResponseDto(notifications.stream().map(this::toDto).toList(), unreadCount);
    }

    /** Marks every unread notification belonging to the caller as read; returns how many changed. */
    @Transactional
    public int markAllRead(AuthenticatedUser caller) {
        return notificationRepository.markAllReadForUser(UUID.fromString(caller.id()));
    }

    private boolean isAllowed(NotificationPrefs prefs, String type) {
        return switch (type) {
            case Notification.TYPE_PATIENT_ASSIGNED -> prefs.patientAssigned();
            case Notification.TYPE_PROCESSING_ERROR -> prefs.processingErrors();
            case Notification.TYPE_SYSTEM_ANNOUNCEMENT -> prefs.systemAnnouncements();
            default -> true;
        };
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId().toString(), n.getType(), n.getTitle(), n.getMessage(),
                n.isRead(), n.getRelatedEntityType(), n.getRelatedEntityId(), n.getCreatedAt());
    }
}
