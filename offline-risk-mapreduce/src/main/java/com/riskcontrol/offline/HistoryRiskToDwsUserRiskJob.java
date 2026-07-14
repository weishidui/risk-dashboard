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
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class HistoryRiskToDwsUserRiskJob extends Configured implements Tool {
    public static class JoinMapper extends Mapper<Object, Text, Text, Text> {
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
                String userId = text(node, "user_id");
                if (!userId.isEmpty()) {
                    outKey.set(transId);
                    outValue.set("U\t" + userId);
                    context.write(outKey, outValue);
                    return;
                }
                String riskCode = text(node, "risk_code");
                int riskScore = intNumber(node, "risk_score");
                if (!riskCode.isEmpty() || riskScore != 0) {
                    outKey.set(transId);
                    outValue.set("R\t" + riskCode + "\t" + riskScore);
                    context.write(outKey, outValue);
                }
            } catch (Exception e) {
                context.getCounter(OfflineConstants.COUNTER_GROUP, "risk_seed_join_bad_rows").increment(1L);
            }
        }

        private static String text(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value == null || value.isNull() ? "" : TextUtil.clean(value.asText());
        }

        private static int intNumber(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value == null || !value.isNumber() ? 0 : value.asInt();
        }
    }

    public static class JoinReducer extends Reducer<Text, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            String userId = "";
            int score = 0;
            TreeSet<String> codes = new TreeSet<String>();
            for (Text value : values) {
                String[] p = value.toString().split("\t", -1);
                if (p.length >= 2 && "U".equals(p[0])) {
                    userId = p[1];
                } else if (p.length >= 3 && "R".equals(p[0])) {
                    if (!p[1].isEmpty()) {
                        codes.add(p[1]);
                    }
                    try {
                        score += Integer.parseInt(p[2]);
                    } catch (NumberFormatException ignored) {
                        context.getCounter(OfflineConstants.COUNTER_GROUP, "risk_seed_bad_score").increment(1L);
                    }
                }
            }
            if (!userId.isEmpty() && (score != 0 || !codes.isEmpty())) {
                outKey.set(userId);
                outValue.set(score + "\t" + TextUtil.join(codes));
                context.write(outKey, outValue);
            }
        }
    }

    public static class UserRiskMapper extends Mapper<Object, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            String[] p = value.toString().split("\t", -1);
            if (p.length < 3 || p[0].isEmpty()) {
                return;
            }
            outKey.set(p[0]);
            outValue.set(p[1] + "\t" + p[2]);
            context.write(outKey, outValue);
        }
    }

    public static class UserRiskReducer extends Reducer<Text, Text, NullWritable, Text> {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private final Text outValue = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            int score = 0;
            Map<String, Integer> codes = new TreeMap<String, Integer>();
            for (Text value : values) {
                String[] p = value.toString().split("\t", -1);
                if (p.length < 2) {
                    continue;
                }
                try {
                    score += Integer.parseInt(p[0]);
                } catch (NumberFormatException ignored) {
                    context.getCounter(OfflineConstants.COUNTER_GROUP, "user_risk_bad_score").increment(1L);
                }
                for (String code : TextUtil.splitCsv(p[1])) {
                    TextUtil.increment(codes, code);
                }
            }
            ObjectNode node = MAPPER.createObjectNode();
            node.put("user_id", key.toString());
            node.put("dt", context.getConfiguration().get("offline.dt", ""));
            node.put("risk_score", score);
            node.put("risk_tags", TextUtil.topN(codes, 20));
            outValue.set(MAPPER.writeValueAsString(node));
            context.write(NullWritable.get(), outValue);
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: HistoryRiskToDwsUserRiskJob <transaction_ods_path> <history_risk_seed_ods_path> <user_risk_output_path>");
            return 2;
        }
        Path temp = new Path(args[2] + "_tmp_by_trans");
        Path output = new Path(args[2]);
        JobUtil.deleteOutputIfExists(getConf(), temp);
        JobUtil.deleteOutputIfExists(getConf(), output);

        Job join = Job.getInstance(getConf(), "risk-offline-risk-seed-join-trans");
        join.setJarByClass(HistoryRiskToDwsUserRiskJob.class);
        join.setMapperClass(JoinMapper.class);
        join.setReducerClass(JoinReducer.class);
        join.setMapOutputKeyClass(Text.class);
        join.setMapOutputValueClass(Text.class);
        join.setOutputKeyClass(Text.class);
        join.setOutputValueClass(Text.class);
        join.getConfiguration().setBoolean("mapreduce.input.fileinputformat.input.dir.recursive", true);
        FileInputFormat.addInputPath(join, new Path(args[0]));
        FileInputFormat.addInputPath(join, new Path(args[1]));
        FileOutputFormat.setOutputPath(join, temp);
        if (!join.waitForCompletion(true)) {
            return 1;
        }

        Job aggregate = Job.getInstance(getConf(), "risk-offline-dws-user-risk");
        aggregate.setJarByClass(HistoryRiskToDwsUserRiskJob.class);
        aggregate.setMapperClass(UserRiskMapper.class);
        aggregate.setReducerClass(UserRiskReducer.class);
        aggregate.setMapOutputKeyClass(Text.class);
        aggregate.setMapOutputValueClass(Text.class);
        aggregate.setOutputKeyClass(NullWritable.class);
        aggregate.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(aggregate, temp);
        FileOutputFormat.setOutputPath(aggregate, output);
        int exit = aggregate.waitForCompletion(true) ? 0 : 1;
        JobUtil.deleteOutputIfExists(getConf(), temp);
        return exit;
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new HistoryRiskToDwsUserRiskJob(), args));
    }
}
