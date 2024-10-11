package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.impl.storage.db.DB.getOne;

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

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
	
	private static Shorts instance;
	
	synchronized public static Shorts getInstance() {
		if( instance == null )
			instance = new JavaShorts();
		return instance;
	}
	
	private JavaShorts() {}
	
	
	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult( okUser(userId, password), user -> {
			
			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoApplication.BASE_URI, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);

			return errorOrValue(DB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);

		//var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
		var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
		var likes = DB.sql(query, Long.class).value();
		return errorOrValue( getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token( likes.size()));
	}

	
	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {

			return errorOrResult( okUser( shrt.getOwnerId(), password), user -> {
				return DB.transaction( hibernate -> {

					hibernate.remove(shrt);

					var query = format("DELETE FROM Likes l WHERE l.shortId = '%s'", shortId);
					hibernate.createNativeQuery( query, Likes.class).executeUpdate();
					String blobUrl = shrt.getBlobUrl();
					String queryParam = "token=";
					String token = blobUrl.substring(blobUrl.indexOf(queryParam) + queryParam.length());
					JavaBlobs.getInstance().delete(shortId, token);
				});
			});
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);
		return errorOrValue( okUser(userId), DB.sql( query, String.class));
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));
	
		
		return errorOrResult( okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			return errorOrVoid( okUser( userId2), isFollowing ? DB.insertOne( f ) : DB.deleteOne( f ));	
		});			
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);		
		return errorOrValue( okUser(userId, password), DB.sql(query, String.class));
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		
		return errorOrResult( getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			return errorOrVoid( okUser( userId, password), isLiked ? DB.insertOne( l ) : DB.deleteOne( l ));	
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {
			
			var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);					
			
			return errorOrValue( okUser( shrt.getOwnerId(), password ), DB.sql(query, String.class));
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		final var QUERY_FMT = """
				SELECT s.shortId, s.timestamp FROM Short s WHERE	s.ownerId = '%s'				
				UNION			
				SELECT s.shortId, s.timestamp FROM Short s, Following f 
					WHERE 
						f.followee = s.ownerId AND f.follower = '%s' 
				ORDER BY s.timestamp DESC""";

		return errorOrValue( okUser( userId, password), DB.sql( format(QUERY_FMT, userId, userId), String.class));		
	}
		
	protected Result<User> okUser( String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}
	
	private Result<Void> okUser( String userId ) {
		var res = okUser( userId, "");
		if( res.error() == FORBIDDEN )
			return ok();
		else
			return error( res.error() );
	}
	
	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if( ! Token.isValid(Token.get(userId), userId ) )
			return error(FORBIDDEN);

		if(TukanoApplication.COSMOS_DB) {

			//delete shorts
			var q1 = format("SELECT * FROM shorts WHERE shorts.ownerId = \"%s\"", userId);
			List<Short> shortList = DB.sql(q1, Short.class).value();
			for(Short s: shortList)
				DB.deleteOne( s );

			//delete follows
			var q2 = format("SELECT * FROM follow WHERE follow.follower = \"%s\" OR follow.followee = \"%s\"", userId, userId);
			List<Following> followingList = DB.sql(q2, Following.class).value();
			for(Following f: followingList)
				DB.deleteOne( f );

			//delete likes
			var q3 = format("SELECT * FROM likes WHERE likes.ownerId = \"%s\" OR likes.userId = \"%s\"", userId, userId);
			List<Likes> likesList = DB.sql(q3, Likes.class).value();
			for(Likes l: likesList)
				DB.deleteOne( l );

			return Result.ok();
		}
		else {
			return DB.transaction( (hibernate) -> {

				//delete shorts
				var query1 = format("DELETE Short s WHERE s.ownerId = %s", userId);
				hibernate.createQuery(query1, Short.class).executeUpdate();

				//delete follows
				var query2 = format("DELETE Following f WHERE f.follower = \"%s\" OR f.followee = \"%s\"", userId, userId);
				hibernate.createQuery(query2, Following.class).executeUpdate();

				//delete likes
				var query3 = format("DELETE Likes l WHERE l.ownerId = \"%s\" OR l.userId = \"%s\"", userId, userId);
				hibernate.createQuery(query3, Likes.class).executeUpdate();

			});
		}
	}
	
}