package tukano.impl.storage.db.azure;

import tukano.api.Short;


/**
 * Represents a Short, as stored in the database
 */
public class ShortDAO extends Short {
    private String _rid;

    private String _ts;

    public ShortDAO() { }


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
        return "ShortDAO [_rid=" + _rid + ", _ts=" + _ts + ", short=" + super.toString();
    }
}
