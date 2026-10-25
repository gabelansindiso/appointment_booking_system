package com.nsindiso.booking.common;

public final class Masking {
    private Masking () {
    }

    public static String email (String e) {
        int at = e.indexOf('@');
        return at < 1 ? "***" : e.charAt(0) + "***" + e.substring(at);
    }

    public static String phone (String p) {
        return p.length() <= 4 ? "****" : "*".repeat(p.length() - 4) + p.substring(p.length() - 4);
    }
}
