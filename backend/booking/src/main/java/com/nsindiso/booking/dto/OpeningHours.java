package com.nsindiso.booking.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;

@Embeddable
public record OpeningHours(@Column(name = "open_time") LocalTime open,
                           @Column(name = "close_time") LocalTime close) {}
