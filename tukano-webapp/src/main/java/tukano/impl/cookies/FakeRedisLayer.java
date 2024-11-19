package tukano.impl.cookies;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FakeRedisLayer {
	static Map<String, Session> sessions = new ConcurrentHashMap<>();

	private static FakeRedisLayer instance;
	synchronized public static FakeRedisLayer getInstance() {
		if(instance == null )
			instance = new FakeRedisLayer();
		return instance;
	}
	
	
	public static void putSession(Session s) {
		sessions.put(s.getUid(), s);
	}
	
	public static Session getSession(String uid) {
		return sessions.get(uid);
	}
}
