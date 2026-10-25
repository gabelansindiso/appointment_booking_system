package com.nsindiso.booking.service;

import com.nsindiso.booking.enums.Channel;

public interface NotificationSender {
    void send(Channel channel, String recipient, String subject, String body);
}
