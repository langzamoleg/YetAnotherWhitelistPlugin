package org.renwixx.yawl.storage;

import java.util.Map;

public interface WhitelistStorage {
    void init() throws Exception;

    Map<String, WhitelistEntry> loadAll() throws Exception;

    void flush(Map<String, WhitelistEntry> entries) throws Exception;

    void close() throws Exception;
}
