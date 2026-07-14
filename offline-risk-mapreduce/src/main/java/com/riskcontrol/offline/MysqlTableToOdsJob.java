package com.riskcontrol.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;

public class MysqlTableToOdsJob extends Configured implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: MysqlTableToOdsJob <dt> <table_name> <ods_output_dir>");
            return 2;
        }

        String dt = args[0];
        String tableName = args[1];
        if (!tableName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Unsafe table name: " + tableName);
        }
        Path outputDir = new Path(args[2]);
        Path outputFile = new Path(outputDir, "mysql." + tableName + ".full.json");
        FileSystem fs = outputDir.getFileSystem(getConf());
        if (fs.exists(outputDir)) {
            fs.delete(outputDir, true);
        }
        fs.mkdirs(outputDir);

        String defaultSql = "SELECT * FROM " + tableName + " ORDER BY id";
        String sql = getConf().get("offline.mysql.table.to.ods.sql", defaultSql);
        JdbcConfig jdbcConfig = JdbcConfig.from(getConf(), "offline.mysql.table.to.ods.enabled");

        int rows = 0;
        try (Connection connection = jdbcConfig.open();
             Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             FSDataOutputStream out = fs.create(outputFile, true);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            statement.setFetchSize(Integer.MIN_VALUE);
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                ResultSetMetaData meta = resultSet.getMetaData();
                while (resultSet.next()) {
                    ObjectNode node = MAPPER.createObjectNode();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        putValue(node, meta.getColumnLabel(i), resultSet.getObject(i));
                    }
                    node.put("dt", dt);
                    writer.write(MAPPER.writeValueAsString(node));
                    writer.newLine();
                    rows++;
                }
            }
        }

        System.out.println("mysql_table_to_ods_table=" + tableName);
        System.out.println("mysql_table_to_ods_rows=" + rows);
        System.out.println("mysql_table_to_ods_output=" + outputFile);
        return 0;
    }

    private static void putValue(ObjectNode node, String name, Object value) {
        if (value == null) {
            node.put(name, "");
        } else if (value instanceof Integer) {
            node.put(name, (Integer) value);
        } else if (value instanceof Long) {
            node.put(name, (Long) value);
        } else if (value instanceof Float) {
            node.put(name, ((Float) value).doubleValue());
        } else if (value instanceof Double) {
            node.put(name, (Double) value);
        } else if (value instanceof BigDecimal) {
            node.put(name, ((BigDecimal) value).doubleValue());
        } else if (value instanceof Boolean) {
            node.put(name, (Boolean) value);
        } else if (value instanceof Timestamp) {
            node.put(name, ((Timestamp) value).getTime());
        } else {
            node.put(name, TextUtil.clean(String.valueOf(value)));
        }
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new MysqlTableToOdsJob(), args));
    }
}
