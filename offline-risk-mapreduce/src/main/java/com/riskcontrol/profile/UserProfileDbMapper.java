package com.riskcontrol.profile;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class UserProfileDbMapper extends Mapper<LongWritable, TransactionHistoryRecord, Text, Text> {
    private final Text outKey = new Text();
    private final Text outValue = new Text();

    @Override
    protected void map(LongWritable key, TransactionHistoryRecord record, Context context)
            throws IOException, InterruptedException {
        if (record.getUserId() == null || record.getUserId().trim().isEmpty()) {
            context.getCounter("profile", "missing_user_id").increment(1L);
            return;
        }

        outKey.set(record.getUserId().trim());
        outValue.set(record.getAmount()
                + "\t" + record.getTransTimestamp()
                + "\t" + clean(record.getCity())
                + "\t" + clean(record.getDeviceId())
                + "\t" + clean(record.getTransId()));
        context.write(outKey, outValue);
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\t', ' ').trim();
    }
}
