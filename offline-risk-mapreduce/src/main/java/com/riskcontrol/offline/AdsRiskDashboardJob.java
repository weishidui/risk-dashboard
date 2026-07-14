package com.riskcontrol.offline;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/** Aggregates one transaction-risk-detail scan into all offline dashboard ADS outputs. */
public class AdsRiskDashboardJob extends Configured implements Tool {
    public static class DashboardMapper extends Mapper<Object, Text, Text, Text> {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private final SimpleDateFormat hourFormat = new SimpleDateFormat("yyyyMMddHH");
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            try {
                JsonNode row = MAPPER.readTree(value.toString());
                int score = number(row, "risk_score");
                String level = text(row, "risk_level");
                boolean risk = score > 0;
                boolean high = "HIGH".equals(level) || "EXTREME".equals(level);
                boolean extreme = "EXTREME".equals(level);
                double amount = decimal(row, "amount");
                String compact = score + "\t" + (risk ? 1 : 0) + "\t" + (high ? 1 : 0) + "\t"
                        + (extreme ? 1 : 0) + "\t" + amount;

                emit(context, "O\tALL", compact + "\t" + text(row, "user_id") + "\t" + text(row, "device_id")
                        + "\t" + text(row, "counterparty_id"));
                emit(context, "S\t" + scoreBucket(score), "1");
                emitFeature(context, "CHANNEL", defaultText(text(row, "channel")), compact);
                emitFeature(context, "TRANS_TYPE", defaultText(text(row, "trans_type")), compact);
                emitFeature(context, "INPUT_METHOD", defaultText(text(row, "input_method")), compact);
                emitFeature(context, "NETWORK_TYPE", defaultText(text(row, "network_type")), compact);
                emitFeature(context, "ROOT_STATUS", enabled(text(row, "root_jailbreak")) ? "ROOTED" : "NORMAL", compact);
                emitFeature(context, "DEVICE_SCORE", deviceScoreBucket(number(row, "dev_score")), compact);
                emitFeature(context, "LOGIN_FAILURE", loginFailureBucket(number(row, "login_fail_count")), compact);
                emitFeature(context, "CANCEL_RETRY", cancelRetryBucket(number(row, "cancel_retry_count")), compact);
                emitFeature(context, "AMOUNT_RANGE", amountBucket(amount), compact);
                emitFeature(context, "TIME_OF_DAY", timeOfDayBucket(longValue(row, "event_time")), compact);
                if (risk) {
                    emit(context, "P\t" + defaultText(text(row, "province")), compact);
                    emit(context, "C\t" + defaultText(text(row, "city")), compact);
                    emit(context, "T\t" + hourFormat.format(new Date(longValue(row, "event_time"))), compact);
                    emit(context, "B\tCHANNEL\t" + defaultText(text(row, "channel")), compact);
                    emit(context, "B\tTYPE\t" + defaultText(text(row, "trans_type")), compact);
                    emit(context, "U\t" + defaultText(text(row, "user_id")), compact);
                    emit(context, "D\t" + defaultText(text(row, "device_id")), compact);
                    emit(context, "K\t" + defaultText(text(row, "counterparty_id")), compact);
                    JsonNode codes = row.get("rule_codes");
                    JsonNode names = row.get("rule_names");
                    JsonNode categories = row.get("rule_categories");
                    if (codes != null && codes.isArray()) {
                        for (int i = 0; i < codes.size(); i++) {
                            String code = TextUtil.clean(codes.get(i).asText());
                            String name = names != null && names.size() > i ? TextUtil.clean(names.get(i).asText()) : "";
                            String category = categories != null && categories.size() > i
                                    ? TextUtil.clean(categories.get(i).asText()) : "未分类";
                            emit(context, "R\t" + code + "\t" + name + "\t" + category, compact);
                        }
                    }
                }
                if (high) {
                    emit(context, "H\t" + text(row, "trans_id"), value.toString());
                }
            } catch (Exception e) {
                context.getCounter(OfflineConstants.COUNTER_GROUP, "ads_dashboard_bad_detail").increment(1L);
            }
        }

        private void emit(Context context, String key, String value) throws IOException, InterruptedException {
            outKey.set(key);
            outValue.set(value);
            context.write(outKey, outValue);
        }

        private void emitFeature(Context context, String key, String value, String compact) throws IOException, InterruptedException {
            emit(context, "F\t" + key + "\t" + value, compact);
        }
    }

    public static class DashboardReducer extends Reducer<Text, Text, NullWritable, Text> {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private MultipleOutputs<NullWritable, Text> outputs;
        private final Text outValue = new Text();

        @Override
        protected void setup(Context context) {
            outputs = new MultipleOutputs<NullWritable, Text>(context);
        }

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            String[] parts = key.toString().split("\t", -1);
            if (parts.length == 0) return;
            String type = parts[0];
            if ("O".equals(type)) {
                reduceOverview(values, context);
            } else if ("S".equals(type)) {
                long count = countValues(values);
                ObjectNode row = base(context);
                row.put("score_bucket", parts.length > 1 ? parts[1] : "未知");
                row.put("risk_count", count);
                row.put("sort_order", bucketOrder(parts.length > 1 ? parts[1] : ""));
                write("score_distribution", row);
            } else if ("P".equals(type) || "C".equals(type)) {
                Aggregate aggregate = aggregate(values);
                ObjectNode row = base(context);
                row.put("region_name", parts.length > 1 ? parts[1] : "未知");
                putAggregate(row, aggregate);
                write("P".equals(type) ? "province_risk_rank" : "city_risk_rank", row);
            } else if ("R".equals(type)) {
                Aggregate aggregate = aggregate(values);
                ObjectNode row = base(context);
                row.put("rule_code", parts.length > 1 ? parts[1] : "");
                row.put("rule_name", parts.length > 2 ? parts[2] : "");
                row.put("rule_category", parts.length > 3 ? parts[3] : "未分类");
                putAggregate(row, aggregate);
                write("rule_risk_rank", row);
            } else if ("T".equals(type)) {
                Aggregate aggregate = aggregate(values);
                ObjectNode row = base(context);
                row.put("stat_hour", parts.length > 1 ? parts[1] : "");
                putAggregate(row, aggregate);
                write("risk_time_trend", row);
            } else if ("F".equals(type)) {
                Aggregate aggregate = aggregate(values);
                ObjectNode row = base(context);
                row.put("feature_key", parts.length > 1 ? parts[1] : "UNKNOWN");
                row.put("feature_value", parts.length > 2 ? parts[2] : "UNKNOWN");
                putFeatureAggregate(row, aggregate);
                write("risk_feature_distribution", row);
            } else if ("B".equals(type)) {
                Aggregate aggregate = aggregate(values);
                ObjectNode row = base(context);
                row.put("behavior_type", parts.length > 1 ? parts[1] : "OTHER");
                row.put("behavior_name", parts.length > 2 ? parts[2] : "未知");
                putAggregate(row, aggregate);
                write("risk_behavior_distribution", row);
            } else if ("U".equals(type) || "D".equals(type) || "K".equals(type)) {
                Aggregate aggregate = aggregate(values);
                ObjectNode row = base(context);
                row.put("entity_id", parts.length > 1 ? parts[1] : "未知");
                putAggregate(row, aggregate);
                write("U".equals(type) ? "high_risk_user_rank"
                        : "D".equals(type) ? "device_risk_rank" : "counterparty_risk_rank", row);
            } else if ("H".equals(type)) {
                for (Text value : values) {
                    writeRaw("high_risk_transaction", value.toString());
                    break;
                }
            }
        }

        private void reduceOverview(Iterable<Text> values, Context context) throws IOException, InterruptedException {
            long total = 0, risk = 0, high = 0, extreme = 0;
            double totalAmount = 0D, riskAmount = 0D, scoreTotal = 0D;
            Set<String> users = new HashSet<String>();
            Set<String> riskUsers = new HashSet<String>();
            Set<String> highUsers = new HashSet<String>();
            Set<String> devices = new HashSet<String>();
            Set<String> counterparties = new HashSet<String>();
            for (Text value : values) {
                String[] p = value.toString().split("\t", -1);
                if (p.length < 8) continue;
                int score = parseInt(p[0]);
                boolean isRisk = "1".equals(p[1]);
                boolean isHigh = "1".equals(p[2]);
                boolean isExtreme = "1".equals(p[3]);
                double amount = parseDouble(p[4]);
                total++; scoreTotal += score; totalAmount += amount;
                if (isRisk) { risk++; riskAmount += amount; }
                if (isHigh) high++;
                if (isExtreme) extreme++;
                add(users, p[5]); add(devices, p[6]); add(counterparties, p[7]);
                if (isRisk) add(riskUsers, p[5]);
                if (isHigh) add(highUsers, p[5]);
            }
            ObjectNode row = base(context);
            row.put("total_transactions", total);
            row.put("risk_transactions", risk);
            row.put("high_risk_transactions", high);
            row.put("extreme_risk_transactions", extreme);
            row.put("distinct_users", users.size());
            row.put("risk_users", riskUsers.size());
            row.put("high_risk_users", highUsers.size());
            row.put("distinct_devices", devices.size());
            row.put("distinct_counterparties", counterparties.size());
            row.put("total_amount", totalAmount);
            row.put("risk_amount", riskAmount);
            row.put("avg_risk_score", total == 0 ? 0D : scoreTotal / total);
            write("overview", row);
        }

        private Aggregate aggregate(Iterable<Text> values) {
            Aggregate aggregate = new Aggregate();
            for (Text value : values) aggregate.add(value.toString());
            return aggregate;
        }

        private void putAggregate(ObjectNode row, Aggregate aggregate) {
            row.put("risk_count", aggregate.riskCount);
            row.put("high_risk_count", aggregate.highRiskCount);
            row.put("extreme_risk_count", aggregate.extremeRiskCount);
            row.put("risk_amount", aggregate.riskAmount);
            row.put("avg_risk_score", aggregate.riskCount == 0 ? 0D : aggregate.scoreTotal / aggregate.riskCount);
        }

        private void putFeatureAggregate(ObjectNode row, Aggregate aggregate) {
            putAggregate(row, aggregate);
            row.put("total_count", aggregate.totalCount);
            row.put("risk_rate", aggregate.totalCount == 0 ? 0D : (double) aggregate.riskCount / aggregate.totalCount);
            row.put("high_risk_rate", aggregate.totalCount == 0 ? 0D : (double) aggregate.highRiskCount / aggregate.totalCount);
        }

        private long countValues(Iterable<Text> values) {
            long count = 0; for (Text ignored : values) count++; return count;
        }

        private ObjectNode base(Context context) {
            ObjectNode row = MAPPER.createObjectNode();
            row.put("dt", context.getConfiguration().get("offline.dt", ""));
            row.put("window_start", context.getConfiguration().get("offline.window.start", ""));
            row.put("window_end", context.getConfiguration().get("offline.window.end", ""));
            return row;
        }

        private void write(String output, ObjectNode row) throws IOException, InterruptedException {
            writeRaw(output, MAPPER.writeValueAsString(row));
        }

        private void writeRaw(String output, String row) throws IOException, InterruptedException {
            outValue.set(row);
            outputs.write(namedOutput(output), NullWritable.get(), outValue, output + "/part");
        }

        private String namedOutput(String output) {
            if ("score_distribution".equals(output)) return "scoreDistribution";
            if ("province_risk_rank".equals(output)) return "provinceRiskRank";
            if ("city_risk_rank".equals(output)) return "cityRiskRank";
            if ("rule_risk_rank".equals(output)) return "ruleRiskRank";
            if ("risk_time_trend".equals(output)) return "riskTimeTrend";
            if ("risk_feature_distribution".equals(output)) return "riskFeatureDistribution";
            if ("risk_behavior_distribution".equals(output)) return "riskBehaviorDistribution";
            if ("high_risk_transaction".equals(output)) return "highRiskTransaction";
            if ("high_risk_user_rank".equals(output)) return "highRiskUserRank";
            if ("device_risk_rank".equals(output)) return "deviceRiskRank";
            if ("counterparty_risk_rank".equals(output)) return "counterpartyRiskRank";
            return "overview";
        }

        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            outputs.close();
        }
    }

    private static final class Aggregate {
        long totalCount, riskCount, highRiskCount, extremeRiskCount;
        double riskAmount, scoreTotal;
        void add(String value) {
            String[] p = value.split("\t", -1);
            if (p.length < 5) return;
            boolean risk = "1".equals(p[1]);
            totalCount++;
            if (risk) {
                scoreTotal += parseDouble(p[0]);
                riskCount++;
                riskAmount += parseDouble(p[4]);
            }
            highRiskCount += "1".equals(p[2]) ? 1 : 0;
            extremeRiskCount += "1".equals(p[3]) ? 1 : 0;
        }
    }

    private static String scoreBucket(int score) {
        if (score <= 0) return "0";
        if (score <= 20) return "1-20";
        if (score <= 40) return "21-40";
        if (score <= 60) return "41-60";
        if (score <= 80) return "61-80";
        if (score <= 100) return "81-100";
        if (score <= 120) return "101-120";
        return ">120";
    }
    private static int bucketOrder(String bucket) {
        if ("0".equals(bucket)) return 0;
        if ("1-20".equals(bucket)) return 1;
        if ("21-40".equals(bucket)) return 2;
        if ("41-60".equals(bucket)) return 3;
        if ("61-80".equals(bucket)) return 4;
        if ("81-100".equals(bucket)) return 5;
        if ("101-120".equals(bucket)) return 6;
        return 7;
    }
    private static String deviceScoreBucket(int score) { if (score < 40) return "0-39"; if (score < 60) return "40-59"; if (score < 80) return "60-79"; return "80-100"; }
    private static String loginFailureBucket(int count) { if (count <= 0) return "0"; if (count == 1) return "1"; if (count == 2) return "2"; return "3+"; }
    private static String cancelRetryBucket(int count) { if (count <= 0) return "0"; if (count == 1) return "1"; return "2+"; }
    private static String amountBucket(double amount) { if (amount < 1000D) return "0-999"; if (amount < 10000D) return "1K-9.9K"; if (amount < 50000D) return "10K-49.9K"; return "50K+"; }
    private static String timeOfDayBucket(long timestamp) { int hour = Integer.parseInt(new SimpleDateFormat("HH").format(new Date(timestamp))); if (hour < 6) return "00-05"; if (hour < 12) return "06-11"; if (hour < 18) return "12-17"; return "18-23"; }
    private static boolean enabled(String value) { return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value); }
    private static String text(JsonNode node, String field) { JsonNode v = node.get(field); return v == null ? "" : TextUtil.clean(v.asText()); }
    private static int number(JsonNode node, String field) { JsonNode v = node.get(field); return v == null ? 0 : v.asInt(); }
    private static double decimal(JsonNode node, String field) { JsonNode v = node.get(field); return v == null ? 0D : v.asDouble(); }
    private static long longValue(JsonNode node, String field) { JsonNode v = node.get(field); return v == null ? 0L : v.asLong(); }
    private static String defaultText(String value) { return value == null || value.isEmpty() ? "未知" : value; }
    private static int parseInt(String value) { try { return Integer.parseInt(value); } catch (Exception e) { return 0; } }
    private static double parseDouble(String value) { try { return Double.parseDouble(value); } catch (Exception e) { return 0D; } }
    private static void add(Set<String> set, String value) { if (value != null && !value.isEmpty()) set.add(value); }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: AdsRiskDashboardJob <ads_detail_input_path> <ads_dashboard_output_path>");
            return 2;
        }
        Path output = new Path(args[1]);
        JobUtil.deleteOutputIfExists(getConf(), output);
        Job job = Job.getInstance(getConf(), "risk-offline-ads-risk-dashboard");
        job.setJarByClass(AdsRiskDashboardJob.class);
        job.setMapperClass(DashboardMapper.class);
        job.setReducerClass(DashboardReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(1);
        MultipleOutputs.addNamedOutput(job, "overview", TextOutputFormat.class, NullWritable.class, Text.class);
        MultipleOutputs.addNamedOutput(job, "scoreDistribution", TextOutputFormat.class, NullWritable.class, Text.class);
        MultipleOutputs.addNamedOutput(job, "provinceRiskRank", TextOutputFormat.class, NullWritable.class, Text.class);
        MultipleOutputs.addNamedOutput(job, "cityRiskRank", TextOutputFormat.class, NullWritable.class, Text.class);
        MultipleOutputs.addNamedOutput(job, "ruleRiskRank", TextOutputFormat.class, NullWritable.class, Text.class);
        MultipleOutputs.addNamedOutput(job, "riskTimeTrend", TextOutputFormat.class, NullWritable.class, Text.class);
        MultipleOutputs.addNamedOutput(job, "riskFeatureDistribution", TextOutputFormat.class, NullWritable.class, Text.class);
        MultipleOutputs.addNamedOutput(job, "riskBehaviorDistribution", TextOutputFormat.class, NullWritable.class, Text.class);
        MultipleOutputs.addNamedOutput(job, "highRiskTransaction", TextOutputFormat.class, NullWritable.class, Text.class);
        MultipleOutputs.addNamedOutput(job, "highRiskUserRank", TextOutputFormat.class, NullWritable.class, Text.class);
        MultipleOutputs.addNamedOutput(job, "deviceRiskRank", TextOutputFormat.class, NullWritable.class, Text.class);
        MultipleOutputs.addNamedOutput(job, "counterpartyRiskRank", TextOutputFormat.class, NullWritable.class, Text.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, output);
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static void main(String[] args) throws Exception { System.exit(ToolRunner.run(new AdsRiskDashboardJob(), args)); }
}
