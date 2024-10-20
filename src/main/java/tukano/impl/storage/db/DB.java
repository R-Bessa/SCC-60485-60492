package tukano.impl.storage.db;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import org.hibernate.Session;

import org.hsqldb.rights.User;
import tukano.api.Result;
import tukano.api.Short;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoApplication;
import tukano.impl.storage.db.azure.CosmosNoSQL;
import tukano.impl.storage.db.azure.CosmosPostgreSQL;
import tukano.impl.storage.db.hibernate.Hibernate;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.NOT_IMPLEMENTED;
import static tukano.impl.rest.TukanoApplication.USERS_DB_TYPE;
import static tukano.impl.storage.db.azure.CosmosNoSQL.shorts_container;


public class DB {

	public static final String USERS = "users";
	public static final String SHORTS = "shorts";
	public static final String FOLLOWING = "following";
	public static final String LIKES = "likes";
	public static final Database usersDB = initDB(USERS_DB_TYPE, USERS);
	public static final Database shortsDB = initDB(TukanoApplication.SHORTS_DB_TYPE, SHORTS);

	private static Database initDB(DatabaseType db_type, String container) {
		switch (db_type) {
			case COSMOS_DB_NOSQL -> {
				CosmosNoSQL instance = CosmosNoSQL.getInstance();
				instance.init(container);
				return instance;
			}
			case COSMOS_DB_POSTGRESQL -> {
				CosmosPostgreSQL instance = CosmosPostgreSQL.getInstance();
				instance.init();
				return instance;
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

	public static <T> Result<List<T>> countAll(Class<T> clazz, String container, Database db, String attribute, String id) {
		return db.countAll(clazz, container, attribute, id);
	}

	public static Result<List<String>> getFeed(String userId) {
		switch (TukanoApplication.SHORTS_DB_TYPE) {
			case COSMOS_DB_POSTGRESQL -> {
				String query_fmt =
						"""
                        SELECT shortId, timestamp FROM shorts WHERE ownerId = '%s'
                        UNION
                        SELECT s.shortId, s.timestamp FROM shorts s, following f
                        WHERE f.followee = s.ownerId AND f.follower = '%s'
                        ORDER BY s.timestamp DESC
                        """;
				return Result.ok(sql(String.class, query_fmt, shortsDB, userId, userId));
			}
			case HIBERNATE -> {
				String query_fmt =
					"""
					SELECT s.shortId, s.timestamp FROM Short s WHERE s.ownerId = '%s'
					UNION
					SELECT s.shortId, s.timestamp FROM Short s, Following f
					WHERE f.followee = s.ownerId AND f.follower = '%s'
					ORDER BY s.timestamp DESC
					""";

				return Result.ok(sql(String.class, query_fmt, shortsDB, userId, userId));
			}

			case COSMOS_DB_NOSQL -> {
				List<Short> ownShorts = shortsDB.getAll(Short.class, "shorts", "ownerId", userId).value();
				List<String> userFollowee = shortsDB.getAllByAttribute(String.class, "following", "followee", "follower", userId).value();

				String q3_fmt = "SELECT * FROM shorts WHERE ARRAY_CONTAINS(@userFollowee, shorts.ownerId)";
				SqlQuerySpec querySpec = new SqlQuerySpec(q3_fmt, List.of(new SqlParameter("@userFollowee", userFollowee)));
				List<Short> followeeShorts = shorts_container.queryItems(querySpec, new CosmosQueryRequestOptions(), Short.class).stream().toList();

				List<Short> feed = new ArrayList<>();
				feed.addAll(ownShorts);
				feed.addAll(followeeShorts);
				feed.sort((s1, s2) -> Long.compare(s2.getTimestamp(), s1.getTimestamp()));

				return Result.ok(feed.stream().map(Short::getShortId).toList());
			}

			default -> {
				return Result.error(NOT_IMPLEMENTED);
			}
		}

	}

	public static <T> Result<List<T>> getAllByAttribute(Class<T> clazz, String container, String attribute, String param, String match, Database db) {
		return db.getAllByAttribute(clazz, container, attribute, param, match);
	}

	public static <T> Result<?> deleteOne(T obj, Database db) {
		return db.deleteOne(obj);
	}

	public static Result<Void> deleteShort(String shortId) {
		switch (TukanoApplication.SHORTS_DB_TYPE) {
			case COSMOS_DB_NOSQL -> {
				DB.processDeleteShort(shortId, null);
				return Result.ok();
			}
			case HIBERNATE -> {
				return DB.transaction(hibernate -> {
					DB.processDeleteShort(shortId, hibernate);
				});
			}
			case COSMOS_DB_POSTGRESQL -> {
				shortsDB.deleteAll(null, null, SHORTS, "shortId", shortId);
				return Result.ok();
			}

			default -> {
				return Result.error(NOT_IMPLEMENTED);}
		}
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
				shortsDB.deleteAll(null, null, SHORTS, "ownerid", userId);
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

	public static <T> Result<List<T>> searchPattern(Database db, Class<T> clazz, String pattern, String container, String attribute) {

		switch (TukanoApplication.SHORTS_DB_TYPE) {
			case COSMOS_DB_NOSQL -> {
				return db.searchPattern(clazz, pattern, container, "id");
			}
			case HIBERNATE, COSMOS_DB_POSTGRESQL -> {
				return db.searchPattern(clazz, pattern, container, attribute);
			}
            default -> {
				return Result.error(NOT_IMPLEMENTED);}
		}
	}

	public static <T> Result<List<T>> getAllByAttributeID(Class<T> clazz, String container, String attribute, String param, String match, Database db) {
		switch (TukanoApplication.SHORTS_DB_TYPE) {
			case COSMOS_DB_NOSQL -> {
				return getAllByAttribute(clazz, container, "id", param, match, db);
			}
			case HIBERNATE -> {
				String c = container.equals(USERS)? User.class.getSimpleName() : Short.class.getSimpleName();
				return getAllByAttribute(clazz, c, attribute, param, match, db);
			}
			case COSMOS_DB_POSTGRESQL -> {
				return getAllByAttribute(clazz, container, attribute, param, match, db);
			}

			default -> {
				return Result.error(NOT_IMPLEMENTED);}
		}
    }


	private static void processDeleteShort(String shortId, Session session) {
		shortsDB.deleteAll(Short.class, session, "shortId", shortId);
		shortsDB.deleteAll(Likes.class, session, "shortId", shortId);
	}

	private static void processDeleteAllShorts(String userId, Session session) {
		shortsDB.deleteAll(Short.class, session, "ownerId", userId);
		shortsDB.deleteAll(Following.class, session, "follower", userId, "followee", userId);
		shortsDB.deleteAll(Likes.class, session, "ownerId", userId, "userId", userId);
	}
}