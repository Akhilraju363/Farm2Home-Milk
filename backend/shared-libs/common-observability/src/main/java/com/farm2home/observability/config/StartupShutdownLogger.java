package com.farm2home.observability.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.time.Duration;

/**
 * Logs a single summary line when the service becomes ready and another when its context
 * closes, so "is this instance up, on what port/profile, and for how long" is answerable
 * from the log file alone without cross-referencing process manager or orchestrator state.
 */
public class StartupShutdownLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupShutdownLogger.class);

    private final Environment environment;

    public StartupShutdownLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        String appName = environment.getProperty("spring.application.name", "application");
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "N/A"));
        String profiles = String.join(",", environment.getActiveProfiles());
        if (profiles.isBlank()) {
            profiles = "default";
        }
        Duration startupTime = event.getTimeTaken();
        log.info("Application started: name={} port={} profile={} javaVersion={} pid={} startupTimeMs={}",
                appName, port, profiles, System.getProperty("java.version"),
                ProcessHandle.current().pid(), startupTime != null ? startupTime.toMillis() : "N/A");
    }

    @EventListener(ContextClosedEvent.class)
    public void onShutdown(ContextClosedEvent event) {
        String appName = environment.getProperty("spring.application.name", "application");
        log.info("Application shutting down: name={}", appName);
    }
}
