package io.github.phora.androptpb.network;

/**
 * Created by phora on 8/24/15.
 */
public class UploadData {
    private String server_url;
    private String token;
    private String vanity;
    private String uuid;
    private String sha1sum;
    private boolean is_private;
    private Long sunset;

    public UploadData(String server_url, String token, String uuid, String sha1sum, boolean is_private, Long sunset) {
        this.server_url = server_url;
        this.token = token;
        this.uuid = uuid;
        this.sha1sum = sha1sum;
        this.is_private = is_private;
        this.sunset = sunset;
    }

    public String getServerUrl() {
        return server_url;
    }

    public void setServerUrl(String server_url) {
        this.server_url = server_url;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUUID() {
        return uuid;
    }

    public void setUUID(String fpath) {
        this.uuid = fpath;
    }

    public String getSha1sum() {
        return sha1sum;
    }

    public void setSha1sum(String sha1sum) {
        this.sha1sum = sha1sum;
    }

    public boolean getIsPrivate() {
        return is_private;
    }

    public void setIsPrivate(boolean is_private) {
        this.is_private = is_private;
    }

    public Long getSunset() {
        return sunset;
    }

    public void setSunset(Long sunset) {
        this.sunset = sunset;
    }

    public String getVanity() {
        return vanity;
    }

    public void setVanity(String vanity) {
        this.vanity = vanity;
    }
}
