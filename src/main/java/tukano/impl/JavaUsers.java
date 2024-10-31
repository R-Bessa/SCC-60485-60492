package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.impl.storage.cache.RedisCache.getCookieKey;
import static tukano.impl.storage.db.DB.USERS;
import static tukano.impl.storage.db.DB.usersDB;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import tukano.impl.storage.cache.RedisCache;
import tukano.impl.storage.db.DB;


public class JavaUsers implements Users {
	
	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;
	
	synchronized public static Users getInstance() {
		if( instance == null )
			instance = new JavaUsers();
		return instance;
	}
	
	private JavaUsers() {}
	
	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if( badUserInfo( user ) )
			return error(BAD_REQUEST);

		if (RedisCache.checkCookie(user.getPwd()) != null)
			return error(CONFLICT);

		var res = errorOrValue( DB.insertOne( user, usersDB), user.getUserId() );
		if (res.isOK())
			RedisCache.generateCookie(user);

		return res;
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info( () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		User user = RedisCache.checkCookie(pwd);
		if(user != null)
			return Result.ok(user);

		var res = DB.getOne(userId, User.class, usersDB);
		if (!res.isOK())
			return res;

		user = res.value();
		if(!user.getPwd().equals(pwd))
			return error(FORBIDDEN);

		RedisCache.generateCookie(user);

		return Result.ok(user);
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		User updatedUser, oldUser = RedisCache.checkCookie(pwd);
		if(oldUser != null) {
			var res = DB.updateOne( oldUser.updateFrom(other), usersDB);
			if(!res.isOK())
				return  res;

			updatedUser = res.value();
			RedisCache.putValue(getCookieKey(pwd), updatedUser);

			return Result.ok(updatedUser);
		}

		var res = DB.getOne( userId, User.class, usersDB);
		if(!res.isOK())
			return res;
		oldUser = res.value();

		if(!oldUser.getPwd().equals(pwd))
			return error(FORBIDDEN);

		res = DB.updateOne( oldUser.updateFrom(other), usersDB);
		if(!res.isOK())
			return  res;

		updatedUser = res.value();
		RedisCache.putValue(getCookieKey(pwd), updatedUser);

		return Result.ok(updatedUser);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null )
			return error(BAD_REQUEST);

		User user = RedisCache.checkCookie(pwd);

		if(user == null) {
			var res = DB.getOne(userId, User.class, usersDB);
			if (!res.isOK())
				return res;

			user = res.value();
			if (!user.getPwd().equals(pwd))
				return error(FORBIDDEN);
		}

		RedisCache.invalidate(pwd);

		return errorOrResult(DB.getOne( userId, User.class, usersDB), u -> {

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread( () -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd);
				JavaBlobs.getInstance().deleteAllBlobs(userId, pwd);
			}).start();

			return (Result<User>) DB.deleteOne(u, usersDB);
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info( () -> format("searchUsers : patterns = %s\n", pattern));

		var hits = DB.searchPattern(usersDB, User.class, pattern, USERS, "userId")
				.value()
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		return ok(hits);
	}

	
	public static Result<User> validatedUserOrError( Result<User> res, String pwd ) {
		if( res.isOK())
			return res.value().getPwd().equals( pwd ) ? res : error(FORBIDDEN);
		else
			return res;
	}
	
	private boolean badUserInfo( User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}
	
	private boolean badUpdateUserInfo( String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && ! userId.equals( info.getUserId()));
	}
}
