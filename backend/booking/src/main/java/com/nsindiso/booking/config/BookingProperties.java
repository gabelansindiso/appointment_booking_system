package com.nsindiso.booking.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "booking")
public record BookingProperties(Duration minLeadTime, int maxAdvanceDays,
                                Duration rescheduleCutoff, int maxReschedules) {
}
