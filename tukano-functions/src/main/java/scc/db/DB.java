package scc.db;

import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import scc.JavaShorts;
import scc.db.azure.CosmosNoSQL;
import org.hibernate.Session;
import scc.data.Following;
import scc.data.Short;
import scc.data.Likes;
import scc.utils.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.String.format;
import static scc.db.azure.CosmosNoSQL.shorts_container;
import static scc.serverless.HttpFunction.SHORTS_DB_TYPE;
import static scc.serverless.HttpFunction.USERS_DB_TYPE;
import static scc.utils.Result.ErrorCode.NOT_IMPLEMENTED;

public class DB {

	public static final String USERS = "users";
	public static final String SHORTS = "shorts";
	public static final String FOLLOWING = "following";
	public static final String LIKES = "likes";
	public static final Database usersDB = initDB(USERS_DB_TYPE, USERS);
	public static final Database shortsDB = initDB(SHORTS_DB_TYPE, SHORTS);

	private static Database initDB(DatabaseType db_type, String container) {
		CosmosNoSQL instance = CosmosNoSQL.getInstance();
		instance.init(container);
		return instance;
	}


	public static <T> Result<List<T>> sql(String query, Class<T> clazz, Database db) {
		return db.sql(query, clazz);
	}

	public static <T> List<T> sql(Class<T> clazz, String fmt, Database db, Object ... args) {
		return db.sql(format(fmt, args), clazz).value();
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

	public static <T> Result<List<T>> getAllByAttribute(Class<T> clazz, String container, String attribute, String param, String match, Database db) {
		return db.getAllByAttribute(clazz, container, attribute, param, match);
	}

	public static <T> Result<?> deleteOne(T obj, Database db) {
		return db.deleteOne(obj);
	}

	public static Result<Void> deleteShort(String shortId) {
		DB.processDeleteShort(shortId, null);
		return Result.ok();
	}

	public static Result<Void> deleteAllShorts(String userId) {
		DB.processDeleteAllShorts(userId, null);
		return Result.ok();
	}

	public static Result<Short> updateViews(String shortId, int views) {
		var shortRes = JavaShorts.getInstance().getShort(shortId);
		if (!shortRes.isOK())
			return shortRes;

		Short shrt = shortRes.value();
		shrt.updateViews(views);
		return updateOne(shrt, shortsDB);
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
		return db.searchPattern(clazz, pattern, container, "id");
	}

	public static <T> Result<List<T>> getAllByAttributeID(Class<T> clazz, String container, String attribute, String param, String match, Database db) {
		return getAllByAttribute(clazz, container, "id", param, match, db);
    }

	public static Result<List<Short>> getPopular() {
		long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
		String query_fmt = String.format(
				"SELECT TOP 1 * FROM shorts WHERE shorts.timestamp >= %d AND NOT STARTSWITH(shorts.id, \"tukano+\") ORDER BY shorts.views DESC",
				fiveMinutesAgo);
		return Result.ok(sql(Short.class, query_fmt, shortsDB));
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