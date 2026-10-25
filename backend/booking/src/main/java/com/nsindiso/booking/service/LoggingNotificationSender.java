package com.nsindiso.booking.service;

import com.nsindiso.booking.common.Masking;
import com.nsindiso.booking.enums.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class LoggingNotificationSender implements NotificationSender {

    @Override
    public void send(Channel channel, String recipient, String subject, String body) {
        String masked = channel == Channel.EMAIL ? Masking.email(recipient) : Masking.phone(recipient);
        log.info("SIMULATED {} to={} subject=\"{}\"", channel, masked, subject);
        log.debug("SIMULATED {} body: {}", channel, body);
    }
}
