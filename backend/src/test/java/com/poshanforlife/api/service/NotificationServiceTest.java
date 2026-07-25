package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.NotificationListResponseDto;
import com.poshanforlife.api.entity.Notification;
import com.poshanforlife.api.entity.NotificationPrefs;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.repository.NotificationRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FcmPushService fcmPushService;

    private NotificationService notificationService;

    private User recipient;
    private AuthenticatedUser caller;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, fcmPushService);
        recipient = newUser("Dr Priya", Role.DOCTOR);
        caller = new AuthenticatedUser(recipient.getId().toString(), "doctor@poshan.test", Role.DOCTOR);
    }

    @Test
    void create_savesNotificationWithAllFields() {
        UUID relatedId = UUID.randomUUID();

        notificationService.create(recipient, Notification.TYPE_PATIENT_ASSIGNED, "New patient assigned",
                "You've been assigned patient Pat Kumar", "patient", relatedId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(recipient);
        assertThat(saved.getType()).isEqualTo(Notification.TYPE_PATIENT_ASSIGNED);
        assertThat(saved.getTitle()).isEqualTo("New patient assigned");
        assertThat(saved.getMessage()).contains("Pat Kumar");
        assertThat(saved.getRelatedEntityType()).isEqualTo("patient");
        assertThat(saved.getRelatedEntityId()).isEqualTo(relatedId);
    }

    @Test
    void create_patientAssigned_skippedWhenPrefDisabled() {
        recipient.setNotificationPrefs(recipient.getNotificationPrefs()
                .merge(null, false, null, null));

        notificationService.create(recipient, Notification.TYPE_PATIENT_ASSIGNED, "New patient assigned",
                "msg", "patient", UUID.randomUUID());

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void create_processingError_skippedWhenPrefDisabled() {
        recipient.setNotificationPrefs(recipient.getNotificationPrefs()
                .merge(null, null, false, null));

        notificationService.create(recipient, Notification.TYPE_PROCESSING_ERROR, "Report processing failed",
                "msg", "report", UUID.randomUUID());

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void create_systemAnnouncement_skippedWhenPrefDisabled() {
        recipient.setNotificationPrefs(recipient.getNotificationPrefs()
                .merge(null, null, null, false));

        notificationService.create(recipient, Notification.TYPE_SYSTEM_ANNOUNCEMENT, "Heads up", "msg", null, null);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void create_leadFollowup_alwaysSent_noDedicatedPrefField() {
        recipient.setNotificationPrefs(new NotificationPrefs(false, false, false, false));

        notificationService.create(recipient, Notification.TYPE_LEAD_FOLLOWUP, "Follow-up reminder",
                "msg", "lead", UUID.randomUUID());

        verify(notificationRepository).save(any());
    }

    @Test
    void list_unreadOnlyFalse_returnsAllCappedAtLimit() {
        Notification n1 = withId(new Notification());
        n1.setUser(recipient);
        n1.setType(Notification.TYPE_LEAD_FOLLOWUP);
        n1.setTitle("t");
        n1.setMessage("m");
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(recipient.getId()), any()))
                .thenReturn(List.of(n1));
        when(notificationRepository.countByUserIdAndReadFalse(recipient.getId())).thenReturn(3L);

        NotificationListResponseDto result = notificationService.list(caller, 20, false);

        assertThat(result.notifications()).hasSize(1);
        assertThat(result.unreadCount()).isEqualTo(3L);
    }

    @Test
    void list_unreadOnlyTrue_usesUnreadQuery() {
        when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(eq(recipient.getId()), any()))
                .thenReturn(List.of());
        when(notificationRepository.countByUserIdAndReadFalse(recipient.getId())).thenReturn(0L);

        notificationService.list(caller, 20, true);

        verify(notificationRepository).findByUserIdAndReadFalseOrderByCreatedAtDesc(eq(recipient.getId()), any());
        verify(notificationRepository, never()).findByUserIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void list_limitAboveMax_isCappedAt50() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(recipient.getId()), any()))
                .thenReturn(List.of());
        when(notificationRepository.countByUserIdAndReadFalse(recipient.getId())).thenReturn(0L);

        notificationService.list(caller, 500, false);

        ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(eq(recipient.getId()), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void markAllRead_delegatesToRepositoryForCallerId() {
        when(notificationRepository.markAllReadForUser(recipient.getId())).thenReturn(4);

        int updated = notificationService.markAllRead(caller);

        assertThat(updated).isEqualTo(4);
        verify(notificationRepository).markAllReadForUser(recipient.getId());
    }

    private static User newUser(String name, Role role) {
        User user = withId(new User());
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(" ", ".") + "@poshan.test");
        user.setRole(role);
        return user;
    }

    private static <T> T withId(T entity) {
        try {
            Field field = findIdField(entity.getClass());
            field.setAccessible(true);
            field.set(entity, UUID.randomUUID());
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field findIdField(Class<?> type) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField("id");
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("id");
    }
}
