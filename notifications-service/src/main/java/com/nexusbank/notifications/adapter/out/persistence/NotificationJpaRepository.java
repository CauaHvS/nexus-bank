package com.nexusbank.notifications.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    Page<NotificationJpaEntity> findByUserId(String userId, Pageable pageable);

    @Query("SELECT COUNT(n) FROM NotificationJpaEntity n WHERE n.userId = :userId AND n.read = false")
    long countUnreadByUserId(String userId);

    @Modifying
    @Query("UPDATE NotificationJpaEntity n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    void markAllAsReadByUserId(String userId);

    Optional<NotificationJpaEntity> findByIdAndUserId(UUID id, String userId);
}
