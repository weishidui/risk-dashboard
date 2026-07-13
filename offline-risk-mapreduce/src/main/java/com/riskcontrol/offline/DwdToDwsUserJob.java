package com.riskcontrol.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class DwdToDwsUserJob extends Configured implements Tool {
    public static class UserMapper extends Mapper<Object, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            try {
                CleanTransRecord r = CleanTransRecord.parse(value.toString());
                outKey.set(r.userId);
                outValue.set(r.amount + "\t" + r.city + "\t" + r.deviceId + "\t"
                        + r.counterpartyId + "\t" + r.payChannel + "\t" + r.timestamp + "\t" + r.devScore + "\t"
                        + r.dt + "\t" + r.transType + "\t" + r.cancelRetryCount);
                context.write(outKey, outValue);
            } catch (Exception e) {
                context.getCounter(OfflineConstants.COUNTER_GROUP, "dwd_user_bad_rows").increment(1L);
            }
        }
    }

    public static class UserReducer extends Reducer<Text, Text, NullWritable, Text> {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private final Text outValue = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            int count = 0;
            double total = 0D;
            double max = 0D;
            long lastTs = 0L;
            String dt = context.getConfiguration().get("offline.dt", "");
            Set<String> cities = new TreeSet<String>();
            Set<String> devices = new TreeSet<String>();
            Set<String> counterparties = new TreeSet<String>();
            Set<String> payChannels = new TreeSet<String>();
            Set<String> transTypes = new TreeSet<String>();
            int cancelRetryCount = 0;
            int smallTestCount = 0;
            int splitLargeCount = 0;

            for (Text value : values) {
                String[] p = value.toString().split("\t", -1);
                if (p.length < 10) {
                    continue;
                }
                try {
                    double amount = Double.parseDouble(p[0]);
                    long ts = Long.parseLong(p[5]);
                    int cancelRetries = (int) Double.parseDouble(p[9]);
                    total += amount;
                    max = Math.max(max, amount);
                    lastTs = Math.max(lastTs, ts);
                    count++;
                    cancelRetryCount += Math.max(0, cancelRetries);
                    if (amount < 1D) {
                        smallTestCount++;
                    }
                    if (amount >= 45000D && amount <= 49999D) {
                        splitLargeCount++;
                    }
                    add(cities, p[1]);
                    add(devices, p[2]);
                    add(counterparties, p[3]);
                    add(payChannels, p[4]);
                    add(transTypes, p[8]);
                    if (dt.isEmpty()) {
                        dt = p[7];
                    }
                } catch (NumberFormatException ignored) {
                    context.getCounter(OfflineConstants.COUNTER_GROUP, "dwd_user_bad_numbers").increment(1L);
                }
            }
            if (count == 0) {
                return;
            }
            ObjectNode node = MAPPER.createObjectNode();
            node.put("user_id", key.toString());
            node.put("dt", dt);
            node.put("trans_count", count);
            node.put("total_amount", round2(total));
            node.put("avg_amount", round2(total / count));
            node.put("distinct_cities", TextUtil.join(cities));
            node.put("distinct_city_count", cities.size());
            node.put("distinct_devices", TextUtil.join(devices));
            node.put("distinct_device_count", devices.size());
            node.put("distinct_counterparties", TextUtil.join(counterparties));
            node.put("distinct_cpty_count", counterparties.size());
            node.put("max_amount", round2(max));
            node.put("pay_channels_used", TextUtil.join(payChannels));
            node.put("trans_types_used", TextUtil.join(transTypes));
            node.put("last_trans_ts", lastTs);
            node.put("cancel_retry_count", cancelRetryCount);
            node.put("small_test_count", smallTestCount);
            node.put("split_large_count", splitLargeCount);
            outValue.set(MAPPER.writeValueAsString(node));
            context.write(NullWritable.get(), outValue);
        }

        private static void add(Set<String> set, String value) {
            String cleaned = TextUtil.clean(value);
            if (!cleaned.isEmpty()) {
                set.add(cleaned);
            }
        }

        private static double round2(double value) {
            return Double.parseDouble(String.format(Locale.US, "%.2f", value));
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: DwdToDwsUserJob <dwd_input_path> <dws_user_output_path>");
            return 2;
        }
        Path output = new Path(args[1]);
        JobUtil.deleteOutputIfExists(getConf(), output);
        Job job = Job.getInstance(getConf(), "risk-offline-mr2-dwd-to-dws-user");
        job.setJarByClass(DwdToDwsUserJob.class);
        job.setMapperClass(UserMapper.class);
        job.setReducerClass(UserReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, output);
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new DwdToDwsUserJob(), args));
    }
}
