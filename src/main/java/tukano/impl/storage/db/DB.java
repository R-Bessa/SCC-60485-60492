package tukano.impl.storage.db;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.Session;

import tukano.api.Result;
import tukano.api.Short;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoApplication;
import tukano.impl.storage.db.azure.CosmosNoSQL;
import tukano.impl.storage.db.azure.CosmosPostgreSQL;
import tukano.impl.storage.db.hibernate.Hibernate;

import static tukano.api.Result.ErrorCode.NOT_IMPLEMENTED;


public class DB {

	public static final String USERS = "users";
	public static final String SHORTS = "shorts";
	public static final String FOLLOWING = "following";
	public static final String LIKES = "likes";
	public static final Database usersDB = initDB(TukanoApplication.USERS_DB_TYPE, USERS);
	public static final Database shortsDB = initDB(TukanoApplication.SHORTS_DB_TYPE, SHORTS);

	private static Database initDB(DatabaseType db_type, String container) {
		switch (db_type) {
			case COSMOS_DB_NOSQL -> {
				CosmosNoSQL instance = CosmosNoSQL.getInstance();
				instance.init(container);
				return instance;
			}
			case COSMOS_DB_POSTGRESQL -> {
				return CosmosPostgreSQL.getInstance();
			}
			default -> {
				return Hibernate.getInstance();
			}
		}
	}


	public static <T> Result<List<T>> sql(String query, Class<T> clazz, Database db) {
		return db.sql(query, clazz);
	}

	public static <T> List<T> sql(Class<T> clazz, String fmt, Database db, Object ... args) {
		return db.sql(String.format(fmt, args), clazz).value();
	}

	public static <T> Result<T> getOne(String id, Class<T> clazz, Database db) {
		return db.getOne(id, clazz);
	}

	public static <T> Result<List<T>> getAll(Class<T> clazz, String container, Database db, String... args) {
		return db.getAll(clazz, container, args);
	}

	public static <T> Result<List<T>> getAllByAtTribute(Class<T> clazz, String container, String attribute, String param, String match, Database db) {
		return db.getAllByAttribute(clazz, container, attribute, param, match);
	}

	public static Result<List<String>> getFollowers(String userId) {
		switch (TukanoApplication.SHORTS_DB_TYPE) {
			case COSMOS_DB_NOSQL -> {
				List<String> followers = shortsDB.getAllByAttribute(Following.class, FOLLOWING, "follower", "followee", userId)
						.value()
						.stream()
						.map(Following::getFollower)
						.toList();
				return Result.ok(followers);
			}

			case HIBERNATE -> {
				return shortsDB.getAllByAttribute(String.class, FOLLOWING, "follower", "followee", userId);
			}

			case COSMOS_DB_POSTGRESQL -> {
				// TODO
				return Result.ok();
			}

			default -> {
				return Result.error(NOT_IMPLEMENTED);}
		}
	}


	public static <T> Result<?> deleteOne(T obj, Database db) {
		return db.deleteOne(obj);
	}

	public static Result<Void> deleteAllShorts(String userId) {
		switch (TukanoApplication.SHORTS_DB_TYPE) {
			case COSMOS_DB_NOSQL -> {
				DB.processDeleteAllShorts(userId, null);
				return Result.ok();
			}
			case HIBERNATE -> {
				return DB.transaction(hibernate -> {
					DB.processDeleteAllShorts(userId, hibernate);
				});
			}
			case COSMOS_DB_POSTGRESQL -> {
				// TODO
				return Result.ok();
			}

			default -> {
				return Result.error(NOT_IMPLEMENTED);}
		}
	}

	public static <T> Result<T> updateOne(T obj, Database db) {
		return db.updateOne(obj);
	}

	public static <T> Result<T> insertOne(T obj, Database db) {
		return Result.errorOrValue(db.persistOne(obj), obj);
	}

	public static <T> Result<T> transaction(Consumer<Session> c) {
		return shortsDB.execute(c::accept);
	}

	public static <T> Result<T> transaction(Function<Session, Result<T>> func) {
		return shortsDB.execute( func );
	}

	private static void processDeleteAllShorts(String userId, Session session) {
		shortsDB.deleteAll(Short.class, session, "ownerId", userId);
		shortsDB.deleteAll(Following.class, session, "follower", userId, "followee", userId);
		shortsDB.deleteAll(Likes.class, session, "ownerId", userId, "userId", userId);
	}
}