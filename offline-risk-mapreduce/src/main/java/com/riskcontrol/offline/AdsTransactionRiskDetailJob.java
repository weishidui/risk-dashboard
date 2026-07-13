package com.riskcontrol.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.Map;
import java.util.TreeMap;

/** Joins clean transactions with historical rule seeds to create the offline risk-detail fact table. */
public class AdsTransactionRiskDetailJob extends Configured implements Tool {
    public static class DetailMapper extends Mapper<Object, Text, Text, Text> {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.isEmpty()) {
                return;
            }
            try {
                JsonNode node = MAPPER.readTree(line);
                String transId = text(node, "trans_id");
                if (transId.isEmpty()) {
                    return;
                }
                outKey.set(transId);
                String riskCode = text(node, "risk_code");
                if (!riskCode.isEmpty()) {
                    outValue.set("R\t" + riskCode + "\t" + text(node, "risk_name") + "\t"
                            + text(node, "risk_category") + "\t" + integer(node, "risk_score"));
                } else if (!text(node, "user_id").isEmpty()) {
                    outValue.set("T\t" + MAPPER.writeValueAsString(node));
                } else {
                    return;
                }
                context.write(outKey, outValue);
            } catch (Exception e) {
                context.getCounter(OfflineConstants.COUNTER_GROUP, "ads_detail_bad_input").increment(1L);
            }
        }
    }

    public static class DetailReducer extends Reducer<Text, Text, NullWritable, Text> {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private final ProvinceResolver provinceResolver = new ProvinceResolver();
        private final Text outValue = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            JsonNode transaction = null;
            Map<String, RuleSeed> rules = new TreeMap<String, RuleSeed>();
            for (Text value : values) {
                String raw = value.toString();
                if (raw.startsWith("T\t")) {
                    transaction = MAPPER.readTree(raw.substring(2));
                } else if (raw.startsWith("R\t")) {
                    String[] parts = raw.split("\t", -1);
                    if (parts.length >= 5) {
                        RuleSeed seed = new RuleSeed(parts[1], parts[2], parts[3], parseInt(parts[4]));
                        rules.put(seed.code, seed);
                    }
                }
            }
            if (transaction == null) {
                return;
            }

            int rawScore = 0;
            ArrayNode codes = MAPPER.createArrayNode();
            ArrayNode names = MAPPER.createArrayNode();
            ArrayNode categories = MAPPER.createArrayNode();
            for (RuleSeed seed : rules.values()) {
                rawScore += seed.score;
                codes.add(seed.code);
                names.add(seed.name);
                categories.add(seed.category);
            }
            int score = rawScore;
            String city = text(transaction, "city");
            ObjectNode row = MAPPER.createObjectNode();
            row.put("dt", context.getConfiguration().get("offline.dt", ""));
            row.put("window_start", context.getConfiguration().get("offline.window.start", ""));
            row.put("window_end", context.getConfiguration().get("offline.window.end", ""));
            row.put("trans_id", key.toString());
            row.put("user_id", text(transaction, "user_id"));
            row.put("province", provinceResolver.resolve(city));
            row.put("city", city);
            row.put("risk_score", score);
            row.put("raw_risk_score", rawScore);
            row.put("risk_level", riskLevel(score));
            row.set("rule_codes", codes);
            row.set("rule_names", names);
            row.set("rule_categories", categories);
            row.put("amount", decimal(transaction, "amount"));
            row.put("channel", text(transaction, "pay_channel"));
            row.put("trans_type", text(transaction, "trans_type"));
            row.put("input_method", text(transaction, "input_method"));
            row.put("network_type", text(transaction, "network_type"));
            row.put("root_jailbreak", text(transaction, "root_jailbreak"));
            row.put("dev_score", integer(transaction, "dev_score"));
            row.put("login_fail_count", integer(transaction, "login_fail_count"));
            row.put("cancel_retry_count", integer(transaction, "cancel_retry_count"));
            row.put("event_time", longValue(transaction, "timestamp", longValue(transaction, "trans_timestamp", 0L)));
            row.put("device_id", text(transaction, "device_id"));
            row.put("counterparty_id", text(transaction, "counterparty_id"));
            outValue.set(MAPPER.writeValueAsString(row));
            context.write(NullWritable.get(), outValue);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : TextUtil.clean(value.asText());
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? 0 : value.asInt();
    }

    private static double decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? 0D : value.asDouble();
    }

    private static long longValue(JsonNode node, String field, long fallback) {
        JsonNode value = node.get(field);
        return value == null ? fallback : value.asLong();
    }

    static String riskLevel(int score) {
        if (score <= 0) return "NONE";
        if (score <= 40) return "LOW";
        if (score <= 70) return "MEDIUM";
        if (score <= 120) return "HIGH";
        return "EXTREME";
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return 0; }
    }

    private static final class RuleSeed {
        final String code; final String name; final String category; final int score;
        RuleSeed(String code, String name, String category, int score) {
            this.code = code; this.name = name; this.category = category; this.score = score;
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: AdsTransactionRiskDetailJob <dwd_transaction_path> <risk_seed_ods_path> <ads_detail_output_path>");
            return 2;
        }
        Path output = new Path(args[2]);
        JobUtil.deleteOutputIfExists(getConf(), output);
        Job job = Job.getInstance(getConf(), "risk-offline-ads-transaction-risk-detail");
        job.setJarByClass(AdsTransactionRiskDetailJob.class);
        job.setMapperClass(DetailMapper.class);
        job.setReducerClass(DetailReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        job.getConfiguration().setBoolean("mapreduce.input.fileinputformat.input.dir.recursive", true);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileInputFormat.addInputPath(job, new Path(args[1]));
        FileOutputFormat.setOutputPath(job, output);
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new AdsTransactionRiskDetailJob(), args));
    }
}
