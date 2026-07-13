package com.riskcontrol.offline;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.IOException;

final class JobUtil {
    private JobUtil() {
    }

    static void deleteOutputIfExists(Configuration conf, Path outputPath) throws IOException {
        FileSystem fs = outputPath.getFileSystem(conf);
        if (fs.exists(outputPath)) {
            fs.delete(outputPath, true);
        }
    }
}
