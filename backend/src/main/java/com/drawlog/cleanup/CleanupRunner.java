package com.drawlog.cleanup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class CleanupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CleanupRunner.class);

    private final CleanupService cleanupService;
    private final ConfigurableApplicationContext context;
    private final boolean dryRun;
    private final boolean apply;
    private final boolean exitAfterRun;

    public CleanupRunner(CleanupService cleanupService,
                         ConfigurableApplicationContext context,
                         @Value("${cleanup.dry-run:false}") boolean dryRun,
                         @Value("${cleanup.apply:false}") boolean apply,
                         @Value("${cleanup.exit-after-run:true}") boolean exitAfterRun) {
        this.cleanupService = cleanupService;
        this.context = context;
        this.dryRun = dryRun;
        this.apply = apply;
        this.exitAfterRun = exitAfterRun;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!dryRun && !apply) return;
        CleanupReport report = cleanupService.cleanup(apply);
        report.lines().forEach(line -> {
            log.info(line);
            System.out.println(line);
        });
        if (exitAfterRun) {
            context.close();
        }
    }
}
