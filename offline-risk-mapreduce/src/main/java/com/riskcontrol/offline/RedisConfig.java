package com.riskcontrol.offline;

import org.apache.hadoop.conf.Configuration;
import redis.clients.jedis.Jedis;

final class RedisConfig {
    final boolean enabled;
    final String host;
    final int port;
    final String password;
    final int ttlSeconds;
    final int timeoutMillis;

    private RedisConfig(boolean enabled, String host, int port, String password, int ttlSeconds, int timeoutMillis) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.password = password;
        this.ttlSeconds = ttlSeconds;
        this.timeoutMillis = timeoutMillis;
    }

    static RedisConfig from(Configuration conf) {
        return new RedisConfig(
                conf.getBoolean("offline.redis.enabled", true),
                conf.get("offline.redis.host", "192.168.154.104"),
                conf.getInt("offline.redis.port", 6379),
                conf.get("offline.redis.password", "123456"),
                conf.getInt("offline.redis.ttl.seconds", 0),
                conf.getInt("offline.redis.timeout.ms", 30000));
    }

    Jedis open() {
        if (!enabled) {
            return null;
        }
        Jedis jedis = new Jedis(host, port, timeoutMillis);
        if (password != null && !password.trim().isEmpty()) {
            jedis.auth(password);
        }
        return jedis;
    }
}
