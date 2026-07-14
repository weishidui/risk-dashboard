package com.riskcontrol.profile;

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.lib.db.DBWritable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TransactionHistoryRecord implements Writable, DBWritable {
    private String transId;
    private String userId;
    private double amount;
    private long transTimestamp;
    private String city;
    private String deviceId;

    public String getTransId() {
        return transId;
    }

    public String getUserId() {
        return userId;
    }

    public double getAmount() {
        return amount;
    }

    public long getTransTimestamp() {
        return transTimestamp;
    }

    public String getCity() {
        return city;
    }

    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeUTF(nullToEmpty(transId));
        out.writeUTF(nullToEmpty(userId));
        out.writeDouble(amount);
        out.writeLong(transTimestamp);
        out.writeUTF(nullToEmpty(city));
        out.writeUTF(nullToEmpty(deviceId));
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        transId = in.readUTF();
        userId = in.readUTF();
        amount = in.readDouble();
        transTimestamp = in.readLong();
        city = in.readUTF();
        deviceId = in.readUTF();
    }

    @Override
    public void write(PreparedStatement statement) throws SQLException {
        statement.setString(1, transId);
        statement.setString(2, userId);
        statement.setDouble(3, amount);
        statement.setLong(4, transTimestamp);
        statement.setString(5, city);
        statement.setString(6, deviceId);
    }

    @Override
    public void readFields(ResultSet resultSet) throws SQLException {
        transId = resultSet.getString("trans_id");
        userId = resultSet.getString("user_id");
        amount = resultSet.getDouble("amount");
        transTimestamp = resultSet.getLong("trans_timestamp");
        city = resultSet.getString("city");
        deviceId = resultSet.getString("device_id");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
