package tukano.impl.storage.cache;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import tukano.api.Result;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;

import java.util.List;

public class RedisCache {
	private static final String RedisHostname = "scc-cache-60485.redis.cache.windows.net";
	private static final String RedisKey = "R28kzYBcQ2NZXUavt5kJsUvXOJAsYplXYAzCaJqByS4=";
	private static final int REDIS_PORT = 6380;
	private static final int REDIS_TIMEOUT = 1000;
	private static final boolean Redis_USE_TLS = true;
	private static final String USER_KEY_PREFIX = "user-";
	private static final String SHORT_KEY_PREFIX = "short-";
	private static final String BLOB_KEY_PREFIX = "blob-";
	private static final String LIKE_KEY_PREFIX = "like-";
	private static final String FOLLOWING_KEY_PREFIX = "following-";
	private static final String LIST = "myList";
	private static final int LIST_MAX_SIZE = 5;
	private static final String COUNTER = "myCounter";
	
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


	//TODO store hash of pwd as key, value is the user and set a TTL
	//TODO what logic for the tokens validity?


	public static <T> void put(String key_attribute, T obj) {
		try (var jedis = getCachePool().getResource()) {
			String key = generateKey(key_attribute, obj.getClass());
			String value = JSON.encode(obj);

			jedis.set(key, value);
			System.out.println("Put key " + key);
			System.out.println("Put value " + value);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Result<Object> get(String key) {
		try (var jedis = getCachePool().getResource()) {
			Object value = JSON.decode( jedis.get(key), getClassByPrefix(key.split("-")[0]));

			System.out.println("Get key " + key);
			System.out.println("Put value " + value);
			return Result.ok(value);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	public static <T> void addList(String list_name, T obj) {
		try (var jedis = getCachePool().getResource()) {
			var cnt = jedis.lpush(list_name, JSON.encode(obj) );
			if (cnt > LIST_MAX_SIZE)
				jedis.ltrim(list_name, 0, LIST_MAX_SIZE - 1);

			System.out.println("Add to the list " + list_name + " the obj " + obj.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Result<List<?>> getList(String list_name) {
		try (var jedis = getCachePool().getResource()) {
			Class<?> clazz = User.class;
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

	public static void invalidate(String key) {
		try (var jedis = getCachePool().getResource()) {
			jedis.del(key);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void setTTL(String key, long ttl) {
		try (var jedis = getCachePool().getResource()) {
			jedis.expire(key, ttl); // seconds

		} catch (Exception e) {
			e.printStackTrace();
		}
	}



	private static <T> String generateKey(String id, Class<T> clazz) {
		String key_prefix;
		if (clazz.equals(User.class))
			key_prefix = USER_KEY_PREFIX;
		else if (clazz.equals(Likes.class) || clazz.equals(Long.class))
			key_prefix = LIKE_KEY_PREFIX;
		else if (clazz.equals(Following.class))
			key_prefix = FOLLOWING_KEY_PREFIX;
		else if (clazz.equals(byte[].class))
			key_prefix = BLOB_KEY_PREFIX;
		else
			key_prefix = SHORT_KEY_PREFIX;

		return key_prefix + id;
	}

	private static Class<?> getClassByPrefix(String prefix) {
		switch (prefix) {
			case USER_KEY_PREFIX -> {
				return User.class;
			}
			case LIKE_KEY_PREFIX -> {
				return Likes.class;
			}
			case FOLLOWING_KEY_PREFIX -> {
				return Following.class;
			}
			case BLOB_KEY_PREFIX -> {
				return byte[].class;
			}
			default -> {
				return Short.class;
			}
		}
	}


}
