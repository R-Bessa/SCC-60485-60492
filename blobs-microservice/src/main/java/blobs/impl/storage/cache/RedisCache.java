package blobs.impl.storage.cache;

import blobs.impl.data.User;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import blobs.impl.cookies.Session;
import blobs.impl.rest.BlobsMicroService;
import utils.Hash;
import utils.Hex;

import static blobs.impl.rest.BlobsMicroService.*;

public class RedisCache {

	private static int redis_port;
	private static boolean redis_use_tls;
	private static final int REDIS_TIMEOUT = 1000;
	public static final String VIEWS_KEY_PREFIX = "views-";
	private static final int COOKIE_VALIDITY = 900; // 15 min


	public static void init() {
		if(BlobsMicroService.DOCKERIZED_REDIS) {
			redis_port = 6379;
			redis_use_tls = false;
		}
		else {
			redis_port = 6380;
			redis_use_tls = true;
		}
	}

	private static JedisPool instance;

	public synchronized static JedisPool getCachePool() {
		if( instance != null)
			return instance;

		var poolConfig = new JedisPoolConfig();
		poolConfig.setMaxTotal(128);
		poolConfig.setMaxIdle(128);
		poolConfig.setMinIdle(16);
		poolConfig.setTestOnBorrow(true);
		poolConfig.setTestOnReturn(true);
		poolConfig.setTestWhileIdle(true);
		poolConfig.setNumTestsPerEvictionRun(3);
		poolConfig.setBlockWhenExhausted(true);
		instance = new JedisPool(poolConfig, REDIS_HOSTNAME, redis_port, REDIS_TIMEOUT, REDIS_KEY, redis_use_tls);
		return instance;
	}

	public static void putSession(Session session) {
		if(!BlobsMicroService.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			jedis.set(session.getUid(), JSON.encode(session));

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Session getSession(String uid) {
		if(!BlobsMicroService.REDIS_CACHE_ON)
			return null;

		try (var jedis = getCachePool().getResource()) {
			var res = jedis.get(uid);
			if(res == null)
				return null;

			return JSON.decode(res, Session.class);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void incrCounter(String prefix, String key) {
		if(!BlobsMicroService.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			jedis.incr(prefix + key);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static String getCookieKey(String pwd) {
		return Hex.of(Hash.sha256(pwd.getBytes()));
	}

	public static User checkCookie(String pwd) {
		try (var jedis = getCachePool().getResource()) {
			String key = getCookieKey(pwd);
			String jsonValue = jedis.get(key);
			if(jsonValue == null)
				return null;

			User u = JSON.decode( jsonValue, User.class);
			jedis.expire(key, COOKIE_VALIDITY);

			return u;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

	}

	public static void generateCookie(User u) {
		try (var jedis = getCachePool().getResource()) {
			String key = getCookieKey(u.getPwd());
			String value = JSON.encode(u);
			jedis.set(key, value);
			jedis.expire(key, COOKIE_VALIDITY);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
