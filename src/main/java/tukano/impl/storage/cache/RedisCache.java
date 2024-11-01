package tukano.impl.storage.cache;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import tukano.api.Result;
import tukano.impl.data.Short;
import tukano.impl.data.User;
import tukano.impl.rest.TukanoApplication;
import tukano.impl.storage.blobs.Blob;
import utils.Hash;
import utils.Hex;

import java.util.List;

public class RedisCache {
	private static final String RedisHostname = "scc-60485-60492.redis.cache.windows.net";
	private static final String RedisKey = "Pxl7UikK1rb8CfLI20nu7q4NJLsUh3W1kAzCaBuBBrc=";
	private static final int REDIS_PORT = 6380;
	private static final int REDIS_TIMEOUT = 1000;
	private static final boolean Redis_USE_TLS = true;

	private static final String FEED_KEY_PREFIX = "feed-";
	private static final String COUNTER_KEY_PREFIX = "likes-";
	private static final int COOKIE_VALIDITY = 900; // 15 min
	private static final int FEED_VALIDITY = 300; // 5 min
	private static final String RECENT_SHORTS = "recent_shorts_list";
	private static final String RECENT_BLOBS = "recent_blobs_list";
	private static final int RECENT_SHORTS_SIZE = 100;
	private static final int RECENT_BLOBS_SIZE = 50;



	// counter of likes per short
	// consistency

	
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

	public static void addRecentBlob(Blob blob) {
		addToList(RECENT_BLOBS, RECENT_BLOBS_SIZE, blob);
	}

	public static Blob getRecentBlob(String blobId) {
		var res = getList(RECENT_BLOBS, Blob.class);
		if(res != null) {
			for(var obj: res.value()) {
				var blob = (Blob) obj;
				if(blob.getBlobId().equals(blobId))
					return blob;
			}
		}

		return null;
	}

	private static void removeRecentBlob(Blob blob) {
		removeFromList(RECENT_BLOBS, JSON.encode(blob));
	}

	public static void removeBlobsByOwner(String userId) {
		var res = getList(RECENT_BLOBS, Blob.class);
		if(res != null) {
			for(var obj: res.value()) {
				var blob = (Blob) obj;
				if(blob.getOwner().equals(userId))
					removeRecentBlob(blob);
			}
		}
	}

	public static void removeBlobById(String blobId) {
		var res = getList(RECENT_BLOBS, Blob.class);
		if(res != null) {
			for(var obj: res.value()) {
				var blob = (Blob) obj;
				if(blob.getBlobId().equals(blobId)) {
					removeRecentBlob(blob);
					break;
				}
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

			System.out.println("Add to the list " + list_name + " the obj " + obj.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void removeFromList(String list_name, String value) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			jedis.lrem(list_name, 1, value);
			System.out.println("Remove from the list " + list_name + " the obj " + value);

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

			System.out.println(list_name);
			for( Object obj : list)
				System.out.println(obj.toString());

			return Result.ok(list);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}


	public static long incrCounter(String shortId) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return -1;

		try (var jedis = getCachePool().getResource()) {
			return jedis.incr(COUNTER_KEY_PREFIX + shortId);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return -1;
	}

	public static long decrCounter(String shortId) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return -1;

		try (var jedis = getCachePool().getResource()) {
			return jedis.decr(COUNTER_KEY_PREFIX + shortId);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return -1;
	}

	public static long getCounter(String shortId) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return -1;

		try (var jedis = getCachePool().getResource()) {
			return Long.parseLong(jedis.get(COUNTER_KEY_PREFIX + shortId));

		} catch (Exception e) {
			e.printStackTrace();
		}

		return -1;
	}

	public static void setCounter(String shortId, long likes) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			jedis.set(COUNTER_KEY_PREFIX + shortId, String.valueOf(likes));

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void removeCounterByUser(String userId) {
		if(!TukanoApplication.REDIS_CACHE_ON)
			return;

		try (var jedis = getCachePool().getResource()) {
			var keys = jedis.keys(COUNTER_KEY_PREFIX + userId);
			for (String key : keys) {
				System.out.println("key to delete " + key);
				jedis.del(key);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
