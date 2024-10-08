package tukano.impl.storage.db;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.Session;

import tukano.api.Result;
import tukano.impl.rest.TukanoApplication;
import tukano.impl.storage.db.azure.CosmosDB;

public class DB {

	private static final Database db = initDB();

	private static Database initDB() {
		return TukanoApplication.COSMOS_DB ? CosmosDB.getInstance() : Hibernate.getInstance();
	}


	public static <T> Result<List<T>> sql(String query, Class<T> clazz) {
		return db.sql(query, clazz);
	}

	public static <T> List<T> sql(Class<T> clazz, String fmt, Object ... args) {
		return db.sql(String.format(fmt, args), clazz).value();
	}

	public static <T> Result<T> getOne(String id, Class<T> clazz) {
		return db.getOne(id, clazz);
	}

	public static <T> Result<?> deleteOne(T obj) {
		return db.deleteOne(obj);
	}

	public static <T> Result<T> updateOne(T obj) {
		return db.updateOne(obj);
	}

	public static <T> Result<T> insertOne(T obj) {
		return Result.errorOrValue(db.persistOne(obj), obj);
	}

	public static <T> Result<T> transaction(Consumer<Session> c) {
		return db.execute(c::accept);
	}

	public static <T> Result<T> transaction(Function<Session, Result<T>> func) {
		return db.execute( func );
	}
}