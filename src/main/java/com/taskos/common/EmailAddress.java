package com.taskos.common;

import java.util.Locale;

public final class EmailAddress {
    private EmailAddress() {
    }

    public static String canonicalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
