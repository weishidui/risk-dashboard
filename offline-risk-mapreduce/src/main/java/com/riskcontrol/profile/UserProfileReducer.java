package com.riskcontrol.profile;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserProfileReducer extends Reducer<Text, Text, Text, Text> {
    private final Text outValue = new Text();

    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context)
            throws IOException, InterruptedException {
        double amountSum = 0.0D;
        long count = 0L;
        long latestTs = Long.MIN_VALUE;
        String latestCity = "";
        Map<String, Integer> cityCounts = new HashMap<String, Integer>();
        Map<String, Integer> deviceCounts = new HashMap<String, Integer>();

        for (Text value : values) {
            String[] parts = value.toString().split("\t", -1);
            if (parts.length < 5) {
                context.getCounter("profile", "bad_mapper_value").increment(1L);
                continue;
            }

            double amount;
            long timestamp;
            try {
                amount = Double.parseDouble(parts[0]);
                timestamp = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                context.getCounter("profile", "bad_number").increment(1L);
                continue;
            }

            String city = parts[2];
            String deviceId = parts[3];

            amountSum += amount;
            count++;
            increment(cityCounts, city);
            increment(deviceCounts, deviceId);
            if (timestamp > latestTs) {
                latestTs = timestamp;
                latestCity = city;
            }
        }

        if (count == 0L) {
            return;
        }

        double avgAmt30d = amountSum / count;
        String commonCities = topN(cityCounts, 3);
        String commonDevs = topN(deviceCounts, 5);
        outValue.set(String.format(java.util.Locale.US, "%.2f\t%s\t%s\t%d\t%s",
                avgAmt30d, commonCities, commonDevs, latestTs, latestCity));
        context.write(key, outValue);
    }

    private static void increment(Map<String, Integer> counts, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        Integer current = counts.get(value);
        counts.put(value, current == null ? 1 : current + 1);
    }

    private static String topN(Map<String, Integer> counts, int n) {
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
}
