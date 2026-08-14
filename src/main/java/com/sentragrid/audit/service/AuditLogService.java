package com.sentragrid.audit.service;

import com.sentragrid.audit.entity.AuditLog;
import com.sentragrid.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(String entityName, Long entityId, String action, String performedBy, String details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .entityName(entityName)
                    .entityId(entityId)
                    .action(action)
                    .performedBy(performedBy)
                    .details(details)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
            log.info("[AUDIT] Entity: {}, ID: {}, Action: {}, User: {}", entityName, entityId, action, performedBy);
        } catch (Exception e) {
            log.error("Failed to write audit log entry: {}", e.getMessage(), e);
        }
    }

    public List<AuditLog> getLogsForEntity(String entityName, Long entityId) {
        return auditLogRepository.findByEntityNameAndEntityId(entityName, entityId);
    }
}
