package com.riskcontrol.offline;

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

public class OdsToDwdJob extends Configured implements Tool {
    public static class CleanMapper extends Mapper<Object, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.isEmpty()) {
                return;
            }
            try {
                CleanTransRecord record = CleanTransRecord.parse(line);
                outKey.set(record.transId);
                outValue.set(record.toJson());
                context.write(outKey, outValue);
            } catch (Exception e) {
                context.getCounter(OfflineConstants.COUNTER_GROUP, "ods_invalid_or_dirty").increment(1L);
            }
        }
    }

    public static class DedupReducer extends Reducer<Text, Text, NullWritable, Text> {
        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            for (Text value : values) {
                context.write(NullWritable.get(), value);
                context.getCounter(OfflineConstants.COUNTER_GROUP, "dwd_clean_rows").increment(1L);
                break;
            }
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: OdsToDwdJob <ods_input_path> <dwd_output_path>");
            return 2;
        }
        Path input = new Path(args[0]);
        Path output = new Path(args[1]);
        JobUtil.deleteOutputIfExists(getConf(), output);

        Job job = Job.getInstance(getConf(), "risk-offline-mr1-ods-to-dwd");
        job.setJarByClass(OdsToDwdJob.class);
        job.setMapperClass(CleanMapper.class);
        job.setReducerClass(DedupReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        job.getConfiguration().setBoolean("mapreduce.input.fileinputformat.input.dir.recursive", true);
        FileInputFormat.addInputPath(job, input);
        FileOutputFormat.setOutputPath(job, output);
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new OdsToDwdJob(), args));
    }
}
