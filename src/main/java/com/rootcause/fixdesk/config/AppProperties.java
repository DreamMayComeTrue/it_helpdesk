package com.rootcause.fixdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under the {@code fixdesk.*} prefix. Values are tuned in
 * application.properties, never hard-coded in the code that uses them.
 * Module-specific prefixes (ollama, triage, escalation, alerts, ai-actions)
 * get their own properties class in their module.
 */
@ConfigurationProperties(prefix = "fixdesk")
public record AppProperties(@DefaultValue Uploads uploads, @DefaultValue Security security) {

    public record Uploads(@DefaultValue("../fixdesk-uploads") String dir) {
    }

    public record Security(
            /** Failed logins for one email + IP pair before it is locked. */
            @DefaultValue("5") int loginMaxFailures,
            /** Window for counting failures, and how long a lock lasts. */
            @DefaultValue("15") int loginWindowMinutes,
            @DefaultValue("8") int passwordMinLength) {
    }
}
