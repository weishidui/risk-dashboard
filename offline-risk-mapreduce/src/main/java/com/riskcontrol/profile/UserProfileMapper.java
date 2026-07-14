package com.riskcontrol.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class UserProfileMapper extends Mapper<Object, Text, Text, Text> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Text outKey = new Text();
    private final Text outValue = new Text();

    @Override
    protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
        String line = value.toString().trim();
        if (line.isEmpty()) {
            return;
        }

        try {
            JsonNode node = OBJECT_MAPPER.readTree(line);
            TransactionRecord record = TransactionRecord.fromJson(node);
            outKey.set(record.getUserId());
            outValue.set(record.getAmount()
                    + "\t" + record.getTimestamp()
                    + "\t" + clean(record.getCity())
                    + "\t" + clean(record.getDeviceId())
                    + "\t" + clean(record.getTransId()));
            context.write(outKey, outValue);
        } catch (Exception e) {
            context.getCounter("profile", "invalid_json_or_missing_field").increment(1L);
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\t', ' ').trim();
    }
}
