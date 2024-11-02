package tukano.impl.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Blob {
    private String blobId;
    private String owner;
    private byte[] bytes;

    public Blob() { }

    @JsonCreator
    public Blob(@JsonProperty("blobId") String blobId, @JsonProperty("owner") String owner, @JsonProperty("bytes") byte[] bytes) {
        this.blobId = blobId;
        this.owner = owner;
        this.bytes = bytes;
    }

    public String getBlobId() {
        return blobId;
    }

    public String getOwner() {
        return owner;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBlobId(String blobId) {
        this.blobId = blobId;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }
}
