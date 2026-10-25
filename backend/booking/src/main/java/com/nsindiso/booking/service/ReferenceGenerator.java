package com.nsindiso.booking.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class ReferenceGenerator {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";   // no ambiguity
    private final SecureRandom random = new SecureRandom();

    String next() {
        StringBuilder sb = new StringBuilder("CAP-");
        for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
