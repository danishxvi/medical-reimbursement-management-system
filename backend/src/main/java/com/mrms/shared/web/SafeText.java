package com.mrms.shared.web;

import java.text.Normalizer;

/**
 * Input sanitisation for free text. Every string that arrives in a JSON body
 * passes through here (see {@link SafeTextConfig}).
 *
 * <p>Text is normalised to Unicode NFC so that visually identical input is
 * stored identically (important for duplicate bill checks and searches).
 * Characters that have no business in a form field are refused instead of
 * silently removed, so what is stored is always exactly what the user saw:
 * <ul>
 *   <li>control characters other than tab, line feed and carriage return;</li>
 *   <li>bidirectional overrides and isolates (used to disguise text such as
 *       "bill_cod.exe" shown as "bill_exe.doc");</li>
 *   <li>unpaired surrogates and Unicode noncharacters.</li>
 * </ul>
 * HTML is not escaped here: output encoding is the job of the view (React
 * escapes everything it renders) and SQL is always parameter bound.
 */
public final class SafeText {

    private SafeText() {
    }

    /** Returns the NFC form of {@code value}, or throws if it contains a refused character. */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        int bad = firstRefused(value);
        if (bad >= 0) {
            throw new UnsafeTextException(value.codePointAt(bad));
        }
        return Normalizer.isNormalized(value, Normalizer.Form.NFC)
                ? value
                : Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    /** Replaces refused characters with {@code replacement}; used where input cannot be refused (file names). */
    public static String strip(String value, char replacement) {
        if (value == null || firstRefused(value) < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            if (refused(cp)) {
                out.append(replacement);
            } else {
                out.appendCodePoint(cp);
            }
        });
        return out.toString();
    }

    /** Index of the first refused character, or -1. */
    static int firstRefused(String value) {
        for (int i = 0; i < value.length(); ) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    return i;
                }
                if (refused(value.codePointAt(i))) {
                    return i;
                }
                i += 2;
                continue;
            }
            if (Character.isLowSurrogate(c) || refused(c)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static boolean refused(int cp) {
        if (cp == '\t' || cp == '\n' || cp == '\r') {
            return false;
        }
        if (cp < 0x20 || (cp >= 0x7F && cp <= 0x9F)) {
            return true;
        }
        if ((cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069)) {
            return true;
        }
        if (cp >= 0xFDD0 && cp <= 0xFDEF) {
            return true;
        }
        return (cp & 0xFFFE) == 0xFFFE;
    }

    /** Raised for text that contains a refused character. */
    public static final class UnsafeTextException extends ApiException {

        UnsafeTextException(int codePoint) {
            super(org.springframework.http.HttpStatus.BAD_REQUEST, "UNSAFE_TEXT",
                    String.format("The text contains a character that is not allowed (U+%04X). "
                            + "Please retype it instead of pasting", codePoint));
        }
    }
}
