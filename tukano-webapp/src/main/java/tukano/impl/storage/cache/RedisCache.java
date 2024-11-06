package tukano.impl.storage.cache;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.ScanParams;
import tukano.api.Result;
import tukano.impl.data.Short;
import tukano.impl.data.User;
import tukano.impl.rest.TukanoApplication;
import utils.Hash;
import utils.Hex;

import java.util.List;

import static tukano.impl.rest.TukanoApplication.REDIS_HOSTNAME;
import static tukano.impl.rest.TukanoApplication.REDIS_KEY;

public class RedisCache {

	private static final int REDIS_PORT = 6380;
	private static final int REDIS_TIMEOUT = 1000;
	private static final boolean Redis_USE_TLS = true;

	private static final String FEED_KEY_PREFIX = "feed-";
	public static final String LIKES_KEY_PREFIX = "likes-";
	public static final String VIEWS_KEY_PREFIX = "views-";
	private static final int COOKIE_VALIDITY = 900; // 15 min
	private static final int FEED_VALIDITY = 60; // 1 min
	private static final String RECENT_SHORTS = "recent_shorts_list";
	private static final int RECENT_SHORTS_SIZE = 100;


	
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
		instance = new JedisPool(poolConfig, REDIS_HOSTNAME, REDIS_PORT, REDIS_TIMEOUT, REDIS_KEY, Redis_USE_TLS);
		return instance;
	}


	public static void generateCookie(User u) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			String key = getCookieKey(u.getPwd());
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

	public static void invalidate(String key) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			jedis.del(key);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void addRecentShort(Short shrt) {
		addToList(RECENT_SHORTS, RECENT_SHORTS_SIZE, shrt);
	}

	public static Short getRecentShort(String shortId) {
		var res = getList(RECENT_SHORTS, Short.class);
		if(res != null) {
			for(var obj: res.value()) {
				var shrt = (Short) obj;
				if(shrt.getShortId().equals(shortId))
					return shrt;
			}
		}

		return null;
	}

	public static void removeRecentShort(Short shrt) {
		removeFromList(RECENT_SHORTS, JSON.encode(shrt));
	}

	public static void removeRecentShorts(String userId) {
		var res = getList(RECENT_SHORTS, Short.class);
		if(res != null) {
			for(var obj: res.value()) {
				var shrt = (Short) obj;
				if(shrt.getOwnerId().equals(userId))
					removeRecentShort(shrt);
			}
		}
	}


	public static void addShortToFeed(String userId, String shortId) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			String key = FEED_KEY_PREFIX + userId;
			var res = getFeed(userId);
			if(res != null && !res.value().isEmpty())
				jedis.lpush(key, shortId);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void addFeed(String userId, List<String> feed) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			String feed_key = FEED_KEY_PREFIX + userId;
			for(String shortId: feed)
				jedis.lpush(feed_key, shortId);

			jedis.expire(feed_key, FEED_VALIDITY);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Result<List<String>> getFeed(String userId) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return null;

		try (var jedis = getCachePool().getResource()) {
			return Result.ok(jedis.lrange(FEED_KEY_PREFIX + userId, 0, -1));

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	public static void removeShortsFromFeed(String userId) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		var feed = getFeed(userId);
		if (feed != null && !feed.value().isEmpty()) {
			feed.value().stream()
					.filter(shortId -> shortId.startsWith(userId + "+"))
					.forEach(shortId -> removeFromList(FEED_KEY_PREFIX + userId, shortId));
		}
	}

	private static <T> void addToList(String list_name, int max_size, T obj) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			var cnt = jedis.lpush(list_name, JSON.encode(obj) );
			if (cnt > max_size)
				jedis.ltrim(list_name, 0, max_size - 1);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void removeFromList(String list_name, String value) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			jedis.lrem(list_name, 1, value);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static <T> Result<List<?>> getList(String list_name, Class<T> clazz) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return null;

		try (var jedis = getCachePool().getResource()) {
			var jsonList = jedis.lrange(list_name, 0, -1);
			var list = jsonList.stream().map(obj -> JSON.decode(obj, clazz)).toList();

			return Result.ok(list);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}


	public static void incrCounter(String prefix, String key) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			jedis.incr(prefix + key);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void decrCounter(String prefix, String key) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			jedis.decr(prefix + key);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static long getCounter(String prefix, String key) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return -1;

		try (var jedis = getCachePool().getResource()) {
			var res = jedis.get(prefix + key);
			if(res == null)
				return -1;
			return Long.parseLong(res);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return -1;
	}

	public static void setCounter(String prefix, String key, long count) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			jedis.set(prefix + key, String.valueOf(count));

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void removeCounterByKey(String prefix, String name) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			String cursor = "0";
			String pattern = prefix + name + "*";

			do {
				var scanResult = jedis.scan(cursor, new ScanParams().match(pattern).count(100));
				for (String key : scanResult.getResult())
					jedis.del(key);
				cursor = scanResult.getCursor();

			} while (!cursor.equals("0"));

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
