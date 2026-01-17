package org.renwixx.yawl.storage;

import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RedisWhitelistStorage implements WhitelistStorage {
    private static final String DEFAULT_SET_KEY = "yawl:whitelist";
    private static final String DEFAULT_META_KEY = "yawl:whitelist:meta";

    private final JedisPool pool;
    private final Logger logger;
    private final String setKey;
    private final String metaKey;

    public RedisWhitelistStorage(String redisUrl, String password, Logger logger) {
        this.logger = logger;
        this.setKey = DEFAULT_SET_KEY;
        this.metaKey = DEFAULT_META_KEY;
        this.pool = new JedisPool(new JedisPoolConfig(), buildUri(redisUrl, password));
    }

    @Override
    public void init() {
        try (Jedis jedis = pool.getResource()) {
            jedis.ping();
        }
    }

    @Override
    public Map<String, WhitelistEntry> loadAll() {
        Map<String, WhitelistEntry> entries = new HashMap<>();
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> meta = jedis.hgetAll(metaKey);
            if (!meta.isEmpty()) {
                for (Map.Entry<String, String> entry : meta.entrySet()) {
                    String canonical = entry.getKey();
                    ParsedValue parsed = parseValue(entry.getValue());
                    entries.put(canonical, new WhitelistEntry(canonical, parsed.originalName, parsed.expiresAtMillis));
                }
                return entries;
            }

            Set<String> members = jedis.smembers(setKey);
            for (String member : members) {
                entries.put(member, new WhitelistEntry(member, member, null));
            }
        }
        logger.info("Loaded {} players from Redis whitelist set", entries.size());
        return entries;
    }

    @Override
    public void flush(Map<String, WhitelistEntry> entries) {
        try (Jedis jedis = pool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            pipeline.del(setKey);
            pipeline.del(metaKey);
            for (WhitelistEntry entry : entries.values()) {
                pipeline.sadd(setKey, entry.getCanonicalName());
                pipeline.hset(metaKey, entry.getCanonicalName(), serializeValue(entry));
            }
            pipeline.sync();
        }
    }

    @Override
    public void close() {
        pool.close();
    }

    private String serializeValue(WhitelistEntry entry) {
        return entry.getOriginalName() + "|" + (entry.getExpiresAtMillis() == null ? "" : entry.getExpiresAtMillis());
    }

    private ParsedValue parseValue(String value) {
        if (value == null) {
            return new ParsedValue("", null);
        }
        String[] parts = value.split("\\|", 2);
        String original = parts[0];
        Long expires = null;
        if (parts.length > 1 && !parts[1].isBlank()) {
            try {
                expires = Long.parseLong(parts[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return new ParsedValue(original, expires);
    }

    private URI buildUri(String redisUrl, String password) {
        URI base = URI.create(redisUrl);
        if (password == null || password.isBlank() || base.getUserInfo() != null) {
            return base;
        }
        try {
            return new URI(base.getScheme(), ":" + password, base.getHost(), base.getPort(), base.getPath(), base.getQuery(), base.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid Redis URL", e);
        }
    }

    private static final class ParsedValue {
        private final String originalName;
        private final Long expiresAtMillis;

        private ParsedValue(String originalName, Long expiresAtMillis) {
            this.originalName = originalName;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
