package org.example.lifecomposer.Service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Milestone 7 audit trail for the admin console.
 *
 * <p>Only identity, action, target and outcome are recorded. Inspected content
 * (feedback text, chat content, profile fields, JSON payloads) is never logged,
 * and no password or hash is ever passed here.</p>
 */
@Component
public class AdminAuditLogger {

    private static final Logger LOG = LogManager.getLogger(AdminAuditLogger.class);

    /** A read query: dataset, validated filters (non-sensitive) and result size. */
    public void query(String admin, String dataset, String filters, long total, int returned,
                      long elapsedMillis) {
        LOG.info("AUDIT event=admin_query admin={} dataset={} filters={} total={} returned={} elapsedMs={}",
                admin, dataset, filters, total, returned, elapsedMillis);
    }

    /** A rejected read query (rate limit or invalid parameter). */
    public void queryRejected(String admin, String dataset, String reason) {
        LOG.warn("AUDIT event=admin_query_rejected admin={} dataset={} reason={}", admin, dataset, reason);
    }

    /** A business action performed from the console. */
    public void action(String admin, String action, String targetType, String targetId, String result) {
        LOG.info("AUDIT event=admin_action admin={} action={} targetType={} targetId={} result={}",
                admin, action, targetType, targetId, result);
    }
}
