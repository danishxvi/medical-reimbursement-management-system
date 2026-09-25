package com.mrms.rates;

import com.mrms.shared.web.SafeText;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads a rate list in CSV form. Used by the seed migration and by the
 * administrator's import, so both apply the same checks.
 *
 * <p>Format: UTF-8, comma separated, RFC 4180 quoting, lines starting with
 * "#" are comments. Required columns: code, name, speciality, non_nabh,
 * nabh, super_speciality. Optional: sr. Every problem is reported with its
 * line number, all at once.
 */
public final class RateCsv {

    public static final int MAX_ROWS = 20_000;
    private static final Pattern CODE = Pattern.compile("[A-Z]{2,4}[0-9]{2,5}[A-Z]?");
    private static final char BOM = (char) 0xFEFF;
    private static final BigDecimal MAX_RATE = new BigDecimal("100000000");
    private static final List<String> REQUIRED =
            List.of("code", "name", "speciality", "non_nabh", "nabh", "super_speciality");

    public record Row(Integer serialNo, String code, String name, String speciality,
                      BigDecimal nonNabh, BigDecimal nabh, BigDecimal superSpeciality) {
    }

    /** All problems found in a file; nothing is imported when there are any. */
    public static final class InvalidException extends RuntimeException {

        private final List<String> problems;

        InvalidException(List<String> problems) {
            super(String.join("; ", problems.subList(0, Math.min(problems.size(), 20))));
            this.problems = problems;
        }

        public List<String> problems() {
            return problems;
        }
    }

    private RateCsv() {
    }

    public static List<Row> parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        List<String> problems = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, Integer> columns = null;
        int lineNo = 0;
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            lineNo++;
            if (lineNo == 1 && !line.isEmpty() && line.charAt(0) == BOM) {
                line = line.substring(1);
            }
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] cells = parseLine(line);
            if (columns == null) {
                columns = header(cells, problems);
                if (!problems.isEmpty()) {
                    throw new InvalidException(problems);
                }
                continue;
            }
            if (rows.size() >= MAX_ROWS) {
                problems.add("More than " + MAX_ROWS + " rows");
                break;
            }
            Row row = row(cells, columns, lineNo, problems);
            if (row != null && !seen.add(row.code())) {
                problems.add("Line " + lineNo + ": code " + row.code() + " appears more than once");
            } else if (row != null) {
                rows.add(row);
            }
        }
        if (columns == null) {
            problems.add("The file has no header row");
        } else if (rows.isEmpty() && problems.isEmpty()) {
            problems.add("The file has no rates");
        }
        if (!problems.isEmpty()) {
            throw new InvalidException(problems);
        }
        return rows;
    }

    private static Map<String, Integer> header(String[] cells, List<String> problems) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < cells.length; i++) {
            columns.put(cells[i].trim().toLowerCase(Locale.ROOT), i);
        }
        for (String required : REQUIRED) {
            if (!columns.containsKey(required)) {
                problems.add("Missing column \"" + required + "\" (required: " + String.join(", ", REQUIRED) + ")");
            }
        }
        return columns;
    }

    private static Row row(String[] cells, Map<String, Integer> columns, int lineNo, List<String> problems) {
        int before = problems.size();
        String code = cell(cells, columns, "code").toUpperCase(Locale.ROOT);
        String name = cell(cells, columns, "name");
        String speciality = cell(cells, columns, "speciality");
        if (!CODE.matcher(code).matches()) {
            problems.add("Line " + lineNo + ": \"" + code + "\" is not a valid rate code");
        }
        if (name.isEmpty() || name.length() > 400) {
            problems.add("Line " + lineNo + ": name is empty or longer than 400 characters");
        }
        if (speciality.isEmpty() || speciality.length() > 120) {
            problems.add("Line " + lineNo + ": speciality is empty or longer than 120 characters");
        }
        try {
            // Same text rules as every other input (control and direction characters refused)
            name = SafeText.clean(name);
            speciality = SafeText.clean(speciality);
        } catch (SafeText.UnsafeTextException e) {
            problems.add("Line " + lineNo + ": " + e.getMessage());
        }
        BigDecimal nonNabh = amount(cells, columns, "non_nabh", lineNo, problems);
        BigDecimal nabh = amount(cells, columns, "nabh", lineNo, problems);
        BigDecimal superSpeciality = amount(cells, columns, "super_speciality", lineNo, problems);
        Integer serial = null;
        if (columns.containsKey("sr") && !cell(cells, columns, "sr").isEmpty()) {
            try {
                serial = Integer.valueOf(cell(cells, columns, "sr"));
            } catch (NumberFormatException e) {
                problems.add("Line " + lineNo + ": serial number is not a whole number");
            }
        }
        if (problems.size() > before) {
            return null;
        }
        return new Row(serial, code, name, speciality, nonNabh, nabh, superSpeciality);
    }

    private static BigDecimal amount(String[] cells, Map<String, Integer> columns, String column, int lineNo,
                                     List<String> problems) {
        String raw = cell(cells, columns, column).replace(",", "");
        try {
            BigDecimal value = new BigDecimal(raw);
            if (value.signum() < 0 || value.compareTo(MAX_RATE) > 0 || value.scale() > 2) {
                problems.add("Line " + lineNo + ": " + column + " must be a positive amount with at most 2 decimals");
                return null;
            }
            return value.setScale(2);
        } catch (NumberFormatException e) {
            problems.add("Line " + lineNo + ": " + column + " \"" + raw + "\" is not an amount");
            return null;
        }
    }

    private static String cell(String[] cells, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        return index == null || index >= cells.length ? "" : cells[index].trim().replaceAll("\\s+", " ");
    }

    /** RFC 4180 line: quoted fields and doubled quotes. */
    static String[] parseLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        cells.add(cell.toString());
        return cells.toArray(String[]::new);
    }
}
