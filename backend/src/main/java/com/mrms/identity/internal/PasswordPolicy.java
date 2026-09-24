package com.mrms.identity.internal;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Password rules: 12 to 128 characters, upper and lower case letters, a
 * digit and a symbol, must not contain the username, must not be a common
 * password, must not repeat one of the recent passwords (checked by the
 * account service against the password history).
 */
@Component
class PasswordPolicy {

    static final int MIN_LENGTH = 12;
    static final int MAX_LENGTH = 128;

    private static final Set<String> COMMON = Set.of(
            "password@123", "password@1234", "welcome@1234", "admin@123456", "qwerty@12345",
            "india@123456", "delhi@123456", "p@ssw0rd1234", "abcd@1234567", "test@1234567");

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "@#$%&*!?";

    private final SecureRandom random = new SecureRandom();

    /** @return human readable problems; empty when the password is acceptable */
    List<String> check(String password, String username) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            problems.add("Use at least " + MIN_LENGTH + " characters");
            return problems;
        }
        if (password.length() > MAX_LENGTH) {
            problems.add("Use at most " + MAX_LENGTH + " characters");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            problems.add("Add an upper case letter");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            problems.add("Add a lower case letter");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            problems.add("Add a digit");
        }
        if (password.chars().allMatch(Character::isLetterOrDigit)) {
            problems.add("Add a symbol such as @ # $ %");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (username != null && !username.isBlank() && lower.contains(username.toLowerCase(Locale.ROOT))) {
            problems.add("Do not include your Employee ID");
        }
        if (COMMON.contains(lower)) {
            problems.add("This password is too common");
        }
        return problems;
    }

    /** Random password that satisfies the policy, used for temporary passwords. */
    String generateTemporary() {
        StringBuilder sb = new StringBuilder();
        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        sb.append(pick(UPPER)).append(pick(LOWER)).append(pick(DIGITS)).append(pick(SYMBOLS));
        while (sb.length() < 14) {
            sb.append(pick(all));
        }
        // Shuffle so the character classes are not in a fixed position
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private char pick(String source) {
        return source.charAt(random.nextInt(source.length()));
    }
}
