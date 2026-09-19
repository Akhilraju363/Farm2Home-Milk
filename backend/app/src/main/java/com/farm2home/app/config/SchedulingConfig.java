package com.farm2home.app.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The single application-level {@code @EnableScheduling}.
 *
 * <p>Five services put {@code @EnableScheduling} on their {@code *Application} class (auth,
 * inventory, order, subscription, payment); those classes are never component-scanned here, so
 * this one enables the scheduler for every embedded {@code @Scheduled} method — with exactly one
 * {@code TaskScheduler} and no risk of double execution.
 *
 * <p><b>Embedded scheduled jobs:</b>
 * <ul>
 *   <li>inventory {@code InventoryItemServiceImpl.alertLowStockItems} — cron {@code 0 0 8 * * *}
 *       (daily low-stock sweep; audit write + a Kafka publish that degrades gracefully)</li>
 *   <li>subscription {@code SubscriptionServiceImpl} — {@code expireEndedSubscriptions}
 *       (01:00), {@code autoResumePausedSubscriptions} (01:05), {@code checkUpcomingRenewals}
 *       (01:10, read-only) — all idempotent</li>
 *   <li>order {@code DailyOrderGenerationService.generateForTomorrow} — cron
 *       {@code 0 0 23 * * *}; generates next-day subscription orders from the local
 *       {@code subscription_snapshots} table. Idempotent (skips a subscription+date that
 *       already has an order). Note: {@code subscription_snapshots} is fed only by the (stopped)
 *       Kafka consumer, so in this no-broker deployment it processes only whatever snapshots
 *       already exist.</li>
 * </ul>
 *
 * <p><b>Prod:</b> ON by default — Render runs this app at <b>exactly one instance</b> (no
 * distributed lock / no ShedLock in this phase). <b>Tests:</b> aggregation tests set
 * {@code farm2home.scheduling.enabled=false} so a run straddling 23:00 (or 01:00 / 08:00)
 * cannot fire the daily-order-generation job and write real {@code "order".orders} rows. This
 * is a test-only guard, not a production behaviour change — documented in GROUP3_REPORT.md.
 */
@Configuration
@ConditionalOnProperty(name = "farm2home.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class SchedulingConfig {
}
