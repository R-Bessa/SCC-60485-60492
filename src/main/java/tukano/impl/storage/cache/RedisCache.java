package tukano.impl.storage.cache;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import tukano.api.Result;
import tukano.api.User;
import tukano.impl.rest.TukanoApplication;
import utils.Hash;
import utils.Hex;

import java.util.List;

public class RedisCache {
	private static final String RedisHostname = "cache-60485.redis.cache.windows.net";
	private static final String RedisKey = "eMGOKN6778QAoYCnYVsPUiX0NsApmcwIPAzCaHRAPQs=";
	private static final int REDIS_PORT = 6380;
	private static final int REDIS_TIMEOUT = 1000;
	private static final boolean Redis_USE_TLS = true;
	private static final String USER_KEY_PREFIX = "user-";
	private static final String SHORT_KEY_PREFIX = "short-";
	private static final int COOKIE_VALIDITY = 900;

	// write through: slower but easier to implement, write back is put in cache and only update db when the cache entry
	// is invalidated


	// sorted set recent users ranked by
	// sorted set recent shorts ranked by timestamp
	// entries for recent blobs
	// entries for  patterns searches
	// getShorts per user
	// getFollowers per user
	// likes per user
	// feeds per user
	// cookies
	// consistency
	// counter of likes per short or hyper log per short
	
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
		instance = new JedisPool(poolConfig, RedisHostname, REDIS_PORT, REDIS_TIMEOUT, RedisKey, Redis_USE_TLS);
		return instance;
	}



	public static void generateCookie(User u) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			String key = getCookieKey(u.getPwd());
			System.out.println("COOKIE: " + key);
			String value = JSON.encode(u);
			jedis.set(key, value);
			jedis.expire(key, COOKIE_VALIDITY);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String getCookieKey(String pwd) {
		return Hex.of(Hash.sha256(pwd.getBytes()));
	}

	public static User checkCookie(String pwd) {
		System.out.println("Check Cookie");
		if(!TukanoApplication.REDIS_CACHE_ON)
			return null;

		try (var jedis = getCachePool().getResource()) {
			String key = getCookieKey(pwd);
			String jsonValue = jedis.get(key);
			if(jsonValue == null)
				return null;

			User u = JSON.decode( jedis.get(key), User.class);
			jedis.expire(key, COOKIE_VALIDITY);

			return u;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

	}



	public static <T> void putValue(String key, T obj) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			String value = JSON.encode(obj);

			jedis.set(key, value);
			System.out.println("Put key " + key);
			System.out.println("Put value " + value);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static <T> Result<Object> getValue(String id, Class<T> clazz) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return  Result.ok(null);

		try (var jedis = getCachePool().getResource()) {
			String key = getKey(id, clazz);
			String jsonValue = jedis.get(key);
			if(jsonValue == null)
				return Result.ok(null);

			Object value = JSON.decode( jedis.get(key), getClassByPrefix(key));
			System.out.println("Get key " + key);
			System.out.println("Put value " + value);
			return Result.ok(value);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return Result.ok(null);
	}


	public static void invalidate(String pwd) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		String key = getCookieKey(pwd);
		try (var jedis = getCachePool().getResource()) {
			jedis.del(key);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	private static <T> void addList(String list_name, int max_size, T obj) {
		try (var jedis = getCachePool().getResource()) {
			var cnt = jedis.lpush(list_name, JSON.encode(obj) );
			if (cnt > max_size)
				jedis.ltrim(list_name, 0, max_size - 1);

			System.out.println("Add to the list " + list_name + " the obj " + obj.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static <T> Result<List<?>> getList(String list_name, Class<T> clazz) {
		try (var jedis = getCachePool().getResource()) {
			var jsonList = jedis.lrange(list_name, 0, -1);
			var list = jsonList.stream().map(obj -> JSON.decode(obj, clazz)).toList();

			System.out.println(list_name);
			for( Object obj : list)
				System.out.println(obj.toString());

			return Result.ok(list);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}


	public static long incrCounter(String key) {
		try (var jedis = getCachePool().getResource()) {
			return jedis.incr(key);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return 0;
	}

	public static long getCounter(String key) {
		try (var jedis = getCachePool().getResource()) {
			return Long.parseLong(jedis.get(key));

		} catch (Exception e) {
			e.printStackTrace();
		}

		return 0;
	}




	private static <T> String getKey(String id, Class<T> clazz) {
		String key_prefix = "";
		if (clazz.equals(User.class))
			key_prefix = USER_KEY_PREFIX;
		else if (clazz.equals(Short.class))
			key_prefix = SHORT_KEY_PREFIX;

		return key_prefix + id;
	}

	private static Class<?> getClassByPrefix(String key) {
		if(key.contains(USER_KEY_PREFIX))
			return User.class;
		else
			return Short.class;

	}

}
