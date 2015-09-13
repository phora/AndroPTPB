package io.github.phora.androptpb.network;

/**
 * Created by phora on 9/13/15.
 */
public class UUIDLocalIDPair {
    private String server;
    private String uuid;
    private long localId;

    public UUIDLocalIDPair(String server, String uuid, long localId) {
        this.server = server;
        this.uuid = uuid;
        this.localId = localId;
    }

    public long getLocalId() {
        return localId;
    }

    public void setLocalId(long localId) {
        this.localId = localId;
    }

    public String getUUID() {
        return uuid;
    }

    public void setUUID(String uuid) {
        this.uuid = uuid;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }
}
