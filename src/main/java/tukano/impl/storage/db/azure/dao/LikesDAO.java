package tukano.impl.storage.db.azure.dao;

import tukano.impl.data.Likes;


/**
 * Represents a Likes, as stored in the database
 */
public class LikesDAO extends Likes {
    private String _rid;

    private String _ts;

    public LikesDAO() { }


    public String get_rid() {
        return _rid;
    }


    public void set_rid(String _rid) {
        this._rid = _rid;
    }


    public String get_ts() {
        return _ts;
    }


    public void set_ts(String _ts) {
        this._ts = _ts;
    }


    @Override
    public String toString() {
        return "LikesDAO [_rid=" + _rid + ", _ts=" + _ts + ", like=" + super.toString();
    }
}