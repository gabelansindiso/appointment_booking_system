package com.nsindiso.booking;

import com.nsindiso.booking.config.BookingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BookingProperties.class)
public class BookingApplication {

    public static void main (String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }

}
