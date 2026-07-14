package com.riskcontrol.offline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class TextUtil {
    private TextUtil() {
    }

    static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    static void increment(Map<String, Integer> counts, String value) {
        String cleaned = clean(value);
        if (cleaned.isEmpty()) {
            return;
        }
        Integer current = counts.get(cleaned);
        counts.put(cleaned, current == null ? 1 : current + 1);
    }

    static String topN(Map<String, Integer> counts, int n) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
        entries.sort(new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> left, Map.Entry<String, Integer> right) {
                int countCompare = right.getValue().compareTo(left.getValue());
                if (countCompare != 0) {
                    return countCompare;
                }
                return left.getKey().compareTo(right.getKey());
            }
        });
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(n, entries.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(entries.get(i).getKey());
        }
        return builder.toString();
    }

    static String join(Iterable<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(cleaned);
        }
        return builder.toString();
    }

    static String[] splitCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return new String[0];
        }
        return csv.split(",");
    }
}
