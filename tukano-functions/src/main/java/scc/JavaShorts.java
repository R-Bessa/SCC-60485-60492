package scc;


import scc.api.Blobs;
import scc.api.Shorts;
import scc.data.Following;
import scc.data.Likes;
import scc.data.User;
import scc.db.DB;
import scc.utils.RedisCache;
import scc.utils.Result;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import scc.data.Short;

import static java.lang.String.format;
import static scc.db.DB.*;
import static scc.serverless.HttpFunction.PRIMARY_BASE_URI;
import static scc.utils.RedisCache.LIKES_KEY_PREFIX;
import static scc.utils.RedisCache.VIEWS_KEY_PREFIX;
import static scc.utils.Result.*;
import static scc.utils.Result.ErrorCode.BAD_REQUEST;
import static scc.utils.Result.ErrorCode.FORBIDDEN;

public class JavaShorts implements Shorts {

	private static final Logger Log = Logger.getLogger(JavaShorts.class.getName());

	private static Shorts instance;

	synchronized public static Shorts getInstance() {
		if (instance == null)
			instance = new JavaShorts();
		return instance;
	}

	private JavaShorts() { }


	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		var res = checkCookie(userId, password);
		if(!res.isOK())
			return error(res.error());

		var shortId = format("%s+%s", userId, UUID.randomUUID());
		var blobUrl = format("%s/%s/%s", PRIMARY_BASE_URI, Blobs.NAME, shortId);
		var shrt = new Short(shortId, userId, blobUrl);
		RedisCache.addRecentShort(shrt);
		RedisCache.addShortToFeed(userId, shortId);

		return errorOrValue(DB.insertOne(shrt, shortsDB), s -> s.copyWithLikes_And_Token(0));
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if (shortId == null)
			return error(BAD_REQUEST);

		var likes = RedisCache.getCounter(LIKES_KEY_PREFIX, shortId);
		if(likes == -1) {
			likes = DB.countAll(Long.class, LIKES, shortsDB, "shortId", shortId).value().get(0);
			RedisCache.setCounter(LIKES_KEY_PREFIX, shortId, likes);
		}

		var shrt = RedisCache.getRecentShort(shortId);
		if(shrt != null)
			return Result.ok(shrt.copyWithLikes_And_Token(likes));

		var res = getOne(shortId, Short.class, shortsDB);
		if (!res.isOK())
			return res;

		shrt = res.value();
		RedisCache.addRecentShort(shrt);

		return Result.ok(shrt.copyWithLikes_And_Token(likes));
	}


	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		var shortRes = getShort(shortId);
		if(!shortRes.isOK())
			return error(shortRes.error());

		Short shrt = shortRes.value();
		var res = checkCookie(shrt.getOwnerId(), password);
		if(!res.isOK())
			return error(res.error());

		String blobUrl = shrt.getBlobUrl();
		String queryParam = "token=";
		String token = blobUrl.substring(blobUrl.indexOf(queryParam) + queryParam.length());
		//JavaBlobs.getInstance().delete(shortId, token);

		RedisCache.removeRecentShort(shrt);
		RedisCache.removeFromList("feed-" + shrt.getOwnerId(), shortId);
		RedisCache.invalidate("likes-" + shortId);

		return DB.deleteShort(shortId);
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		return errorOrValue(okUser(userId), DB.getAllByAttributeID(String.class, SHORTS, "shortId", "ownerId", userId, shortsDB));
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));

		var res = checkCookie(userId1, password);
		if(!res.isOK())
			return error(res.error());

		var f = new Following(userId1, userId2);
		return errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f, shortsDB) : DB.deleteOne(f, shortsDB));
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var res = checkCookie(userId, password);
		if(!res.isOK())
			return error(res.error());

		return DB.getAllByAttribute(String.class, FOLLOWING, "follower", "followee", userId, shortsDB);
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		var res = checkCookie(userId, password);
		if(!res.isOK())
			return error(res.error());

		var shortRes = getShort(shortId);
		if(!shortRes.isOK())
			return error(shortRes.error());

		Short shrt = shortRes.value();
		var l = new Likes(userId, shortId, shrt.getOwnerId());
		var likeRes = isLiked ? DB.insertOne(l, shortsDB) : DB.deleteOne(l, shortsDB);
		if(!likeRes.isOK())
			return error(likeRes.error());

		var likes = RedisCache.getCounter(LIKES_KEY_PREFIX, shortId);
		if(likes == -1) {
			likes = DB.countAll(Long.class, LIKES, shortsDB, "shortId", shortId).value().get(0);
			RedisCache.setCounter(LIKES_KEY_PREFIX, shortId, likes);
		}
		else if(isLiked)
			RedisCache.incrCounter(LIKES_KEY_PREFIX, shortId);
		else
			RedisCache.decrCounter(LIKES_KEY_PREFIX, shortId);

		return Result.ok();
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		var shortRes = getShort(shortId);
		if(!shortRes.isOK())
			return error(shortRes.error());

		Short shrt = shortRes.value();
		var res = checkCookie(shrt.getOwnerId(), password);
		if(!res.isOK())
			return error(res.error());

		return DB.getAllByAttribute(String.class, LIKES, "userId", "shortId", shortId, shortsDB);
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		var res = checkCookie(userId, password);
		if(!res.isOK())
			return error(res.error());

		var feedRes = RedisCache.getFeed(userId);
		if(feedRes != null && !feedRes.value().isEmpty())
			return feedRes;

		feedRes = DB.getFeed(userId);
		if(!feedRes.isOK())
			return feedRes;

		var feed = feedRes.value();
		if(!feed.isEmpty())
			RedisCache.addFeed(userId, feedRes.value());

		return feedRes;
	}

	@Override
	public Result<Void> deleteAllShorts(String userId, String password) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s\n", userId, password));

		var res = checkCookie(userId, password);
		if(!res.isOK())
			return error(res.error());

		RedisCache.removeRecentShorts(userId);
		RedisCache.removeShortsFromFeed(userId);
		RedisCache.removeCounterByKey(LIKES_KEY_PREFIX, userId);
		RedisCache.removeCounterByKey(VIEWS_KEY_PREFIX, userId);

		return DB.deleteAllShorts(userId);
	}

	public static Result<User> okUser(String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}

	private Result<Void> okUser(String userId) {
		var res = okUser(userId, "");
		if (res.error() == FORBIDDEN)
			return ok();
		else
			return error(res.error());
	}

	private Result<User> checkCookie(String userId, String pwd) {
		User user = RedisCache.checkCookie(pwd);
		if (user == null) {
			var res = okUser(userId, pwd);
			if (!res.isOK())
				return res;

			user = res.value();
			if (!user.getPwd().equals(pwd))
				return error(FORBIDDEN);

			RedisCache.generateCookie(user);
		}

		return Result.ok();
	}

}