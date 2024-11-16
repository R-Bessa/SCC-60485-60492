package tukano.impl.cookies;

import com.fasterxml.jackson.annotation.JsonCreator;

public class Session {
    private String uid;
    private String user;

    @JsonCreator
    public Session(String uid, String user) {
        this.uid = uid;
        this.user = user;
    }

    public Session() {}

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }
}
