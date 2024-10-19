package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.impl.storage.db.DB.*;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoApplication;
import tukano.impl.storage.db.DB;

public class JavaShorts implements Shorts {

	private static final Logger Log = Logger.getLogger(JavaShorts.class.getName());

	private static Shorts instance;

	synchronized public static Shorts getInstance() {
		if (instance == null)
			instance = new JavaShorts();
		return instance;
	}

	private JavaShorts() {
	}


	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult(okUser(userId, password), user -> {

			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoApplication.BASE_URI, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);

			return errorOrValue(DB.insertOne(shrt, shortsDB), s -> s.copyWithLikes_And_Token(0));
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if (shortId == null)
			return error(BAD_REQUEST);

		var likes = DB.countAll(Long.class, LIKES, shortsDB, "shortId", shortId).value();
		return errorOrValue(getOne(shortId, Short.class, shortsDB), shrt -> shrt.copyWithLikes_And_Token(likes.get(0)));
	}


	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {

			return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
				String blobUrl = shrt.getBlobUrl();
				String queryParam = "token=";
				String token = blobUrl.substring(blobUrl.indexOf(queryParam) + queryParam.length());
				JavaBlobs.getInstance().delete(shortId, token);
				return DB.deleteShort(shortId);
			});
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		return errorOrValue(okUser(userId), DB.getAllByAttributeID(String.class, SHORTS, "shortId", "ownerId", userId, shortsDB));
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));


		return errorOrResult(okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			return errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f, shortsDB) : DB.deleteOne(f, shortsDB));
		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		return errorOrValue(okUser(userId, password), DB.getAllByAttribute(String.class, FOLLOWING, "follower", "followee", userId, shortsDB));
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));


		return errorOrResult(getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			return errorOrVoid(okUser(userId, password), isLiked ? DB.insertOne(l, shortsDB) : DB.deleteOne(l, shortsDB));
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> errorOrValue(okUser(shrt.getOwnerId(), password),
				DB.getAllByAttribute(String.class, LIKES, "userId", "shortId", shortId, shortsDB)));
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		return errorOrValue(okUser(userId, password), DB.getFeed(userId));
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

	@Override
	public Result<Void> deleteAllShorts(String userId, String password) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s\n", userId, password));

		return errorOrValue(okUser(userId, password), DB.deleteAllShorts(userId));
	}
}