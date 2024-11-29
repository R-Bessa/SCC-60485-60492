package blobs.impl.storage.cache;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import blobs.impl.cookies.Session;
import blobs.impl.rest.BlobsMicroService;

import static blobs.impl.rest.BlobsMicroService.*;

public class RedisCache {

	private static int redis_port;
	private static boolean redis_use_tls;
	private static final int REDIS_TIMEOUT = 1000;
	public static final String VIEWS_KEY_PREFIX = "views-";

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
}
