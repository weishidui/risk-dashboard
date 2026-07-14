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
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Builds inferred cross-province risk flows using each counterparty user's latest city in the analysis window. */
public class AdsCrossRegionRiskFlowJob extends Configured implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static class LatestCityMapper extends Mapper<Object, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();
        @Override protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            try {
                JsonNode row = MAPPER.readTree(value.toString());
                String userId = text(row, "user_id");
                if (userId.isEmpty()) return;
                outKey.set(userId);
                outValue.set(longValue(row, "event_time") + "\t" + text(row, "province") + "\t" + text(row, "city"));
                context.write(outKey, outValue);
            } catch (Exception e) {
                context.getCounter(OfflineConstants.COUNTER_GROUP, "flow_latest_city_bad_rows").increment(1L);
            }
        }
    }

    public static class LatestCityReducer extends Reducer<Text, Text, Text, Text> {
        private final Text outValue = new Text();
        @Override protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            long latest = -1L; String province = ""; String city = "";
            for (Text value : values) {
                String[] p = value.toString().split("\t", -1);
                long time = p.length > 0 ? parseLong(p[0]) : 0L;
                if (time >= latest) { latest = time; province = p.length > 1 ? p[1] : ""; city = p.length > 2 ? p[2] : ""; }
            }
            if (!province.isEmpty()) { outValue.set(province + "\t" + city); context.write(key, outValue); }
        }
    }

    public static class FlowTransactionMapper extends Mapper<Object, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();
        @Override protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            try {
                JsonNode row = MAPPER.readTree(value.toString());
                int score = integer(row, "risk_score");
                String counterparty = text(row, "counterparty_id");
                if (score <= 0 || counterparty.isEmpty()) return;
                outKey.set(counterparty);
                outValue.set("T\t" + text(row, "province") + "\t" + text(row, "city") + "\t" + score + "\t"
                        + (score > 70 ? 1 : 0) + "\t" + (score > 120 ? 1 : 0) + "\t" + decimal(row, "amount"));
                context.write(outKey, outValue);
            } catch (Exception e) {
                context.getCounter(OfflineConstants.COUNTER_GROUP, "flow_transaction_bad_rows").increment(1L);
            }
        }
    }

    public static class UserCityMapper extends Mapper<Object, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();
        @Override protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            String[] p = value.toString().split("\t", -1);
            if (p.length < 2 || p[0].isEmpty()) return;
            outKey.set(p[0]); outValue.set("U\t" + p[1] + "\t" + (p.length > 2 ? p[2] : "")); context.write(outKey, outValue);
        }
    }

    public static class FlowJoinReducer extends Reducer<Text, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();
        @Override protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            String targetProvince = "", targetCity = ""; List<String> transactions = new ArrayList<String>();
            for (Text value : values) {
                String raw = value.toString();
                if (raw.startsWith("U\t")) { String[] p = raw.split("\t", -1); targetProvince = p.length > 1 ? p[1] : ""; targetCity = p.length > 2 ? p[2] : ""; }
                else if (raw.startsWith("T\t")) transactions.add(raw.substring(2));
            }
            if (targetProvince.isEmpty()) return;
            for (String transaction : transactions) {
                String[] p = transaction.split("\t", -1);
                if (p.length < 6 || p[0].isEmpty() || p[0].equals(targetProvince)) continue;
                outKey.set(p[0] + "|" + p[1] + "|" + targetProvince + "|" + targetCity);
                outValue.set(p[2] + "\t1\t" + p[3] + "\t" + p[4] + "\t" + p[5]);
                context.write(outKey, outValue);
            }
        }
    }

    public static class FlowAggMapper extends Mapper<Object, Text, Text, Text> {
        private final Text outKey = new Text(); private final Text outValue = new Text();
        @Override protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            String[] p = value.toString().split("\t", 2);
            if (p.length == 2) { outKey.set(p[0]); outValue.set(p[1]); context.write(outKey, outValue); }
        }
    }

    public static class FlowAggReducer extends Reducer<Text, Text, NullWritable, Text> {
        private final Text outValue = new Text();
        @Override protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            long count = 0, high = 0, extreme = 0; double amount = 0D, scores = 0D;
            for (Text value : values) { String[] p = value.toString().split("\t", -1); if (p.length < 5) continue; scores += parseDouble(p[0]); count += parseLong(p[1]); high += parseLong(p[2]); extreme += parseLong(p[3]); amount += parseDouble(p[4]); }
            String[] regions = key.toString().split("\\|", -1); if (regions.length < 4) return;
            ObjectNode row = MAPPER.createObjectNode();
            row.put("dt", context.getConfiguration().get("offline.dt", ""));
            row.put("from_province", regions[0]); row.put("from_city", regions[1]); row.put("to_province", regions[2]); row.put("to_city", regions[3]);
            row.put("risk_count", count); row.put("high_risk_count", high); row.put("extreme_risk_count", extreme); row.put("risk_amount", amount); row.put("avg_risk_score", count == 0 ? 0D : scores / count);
            outValue.set(MAPPER.writeValueAsString(row)); context.write(NullWritable.get(), outValue);
        }
    }

    @Override public int run(String[] args) throws Exception {
        if (args.length != 2) { System.err.println("Usage: AdsCrossRegionRiskFlowJob <ads_detail_input_path> <ads_cross_region_flow_output_path>"); return 2; }
        Path detail = new Path(args[0]); Path userCity = new Path(args[1] + "_tmp_user_city"); Path rawFlow = new Path(args[1] + "_tmp_raw_flow"); Path output = new Path(args[1]);
        JobUtil.deleteOutputIfExists(getConf(), userCity); JobUtil.deleteOutputIfExists(getConf(), rawFlow); JobUtil.deleteOutputIfExists(getConf(), output);
        Job latest = Job.getInstance(getConf(), "risk-offline-ads-flow-user-city"); latest.setJarByClass(AdsCrossRegionRiskFlowJob.class); latest.setMapperClass(LatestCityMapper.class); latest.setReducerClass(LatestCityReducer.class); latest.setMapOutputKeyClass(Text.class); latest.setMapOutputValueClass(Text.class); latest.setOutputKeyClass(Text.class); latest.setOutputValueClass(Text.class); FileInputFormat.addInputPath(latest, detail); FileOutputFormat.setOutputPath(latest, userCity); if (!latest.waitForCompletion(true)) return 1;
        Job join = Job.getInstance(getConf(), "risk-offline-ads-flow-join"); join.setJarByClass(AdsCrossRegionRiskFlowJob.class); join.setReducerClass(FlowJoinReducer.class); join.setMapOutputKeyClass(Text.class); join.setMapOutputValueClass(Text.class); join.setOutputKeyClass(Text.class); join.setOutputValueClass(Text.class); MultipleInputs.addInputPath(join, detail, TextInputFormat.class, FlowTransactionMapper.class); MultipleInputs.addInputPath(join, userCity, TextInputFormat.class, UserCityMapper.class); FileOutputFormat.setOutputPath(join, rawFlow); if (!join.waitForCompletion(true)) return 1;
        Job aggregate = Job.getInstance(getConf(), "risk-offline-ads-cross-region-flow"); aggregate.setJarByClass(AdsCrossRegionRiskFlowJob.class); aggregate.setMapperClass(FlowAggMapper.class); aggregate.setReducerClass(FlowAggReducer.class); aggregate.setMapOutputKeyClass(Text.class); aggregate.setMapOutputValueClass(Text.class); aggregate.setOutputKeyClass(NullWritable.class); aggregate.setOutputValueClass(Text.class); FileInputFormat.addInputPath(aggregate, rawFlow); FileOutputFormat.setOutputPath(aggregate, output); int exit = aggregate.waitForCompletion(true) ? 0 : 1;
        JobUtil.deleteOutputIfExists(getConf(), userCity); JobUtil.deleteOutputIfExists(getConf(), rawFlow); return exit;
    }

    private static String text(JsonNode n,String f){JsonNode v=n.get(f);return v==null?"":TextUtil.clean(v.asText());}
    private static long longValue(JsonNode n,String f){JsonNode v=n.get(f);return v==null?0L:v.asLong();}
    private static int integer(JsonNode n,String f){JsonNode v=n.get(f);return v==null?0:v.asInt();}
    private static double decimal(JsonNode n,String f){JsonNode v=n.get(f);return v==null?0D:v.asDouble();}
    private static long parseLong(String v){try{return Long.parseLong(v);}catch(Exception e){return 0L;}}
    private static double parseDouble(String v){try{return Double.parseDouble(v);}catch(Exception e){return 0D;}}
    public static void main(String[] args) throws Exception { System.exit(ToolRunner.run(new AdsCrossRegionRiskFlowJob(), args)); }
}
