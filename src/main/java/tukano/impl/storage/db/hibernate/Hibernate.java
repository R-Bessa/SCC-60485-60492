package tukano.impl.storage.db.hibernate;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.exception.ConstraintViolationException;

import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.api.User;
import tukano.impl.storage.db.DB;
import tukano.impl.storage.db.Database;

import static java.lang.String.format;
import static tukano.impl.storage.db.DB.SHORTS_CONTAINER;
import static tukano.impl.storage.db.DB.USERS_CONTAINER;


/**
 * A helper class to perform POJO (Plain Old Java Objects) persistence, using
 * Hibernate and a backing relational database.
 *
 */
public class Hibernate implements Database {

	private static final String HIBERNATE_CFG_FILE = "hibernate.cfg.xml";
	private SessionFactory sessionFactory;
	private static Hibernate instance;

	private Hibernate() {
		try {
			sessionFactory = new Configuration().configure(HIBERNATE_CFG_FILE).buildSessionFactory();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns the Hibernate instance, initializing if necessary. Requires a
	 * configuration file (hibernate.cfg.xml)
	 *
	 * @return Hibernate instance
	 */
	synchronized public static Hibernate getInstance() {
		if (instance == null)
			instance = new Hibernate();
		return instance;
	}

	@Override
	public <T> Result<T>  persistOne(T  obj) {
		return execute( (hibernate) -> {
			hibernate.persist( obj );
		});
	}

	@Override
	public <T> Result<T> updateOne(T obj) {
		return execute( hibernate -> {
			var res = hibernate.merge( obj );
			if( res == null)
				return Result.error( ErrorCode.NOT_FOUND );
			
			return Result.ok( res );
		});
	}

	@Override
	public <T> Result<?> deleteOne(T obj) {
		return execute( hibernate -> {
			hibernate.remove( obj );
			return Result.ok( obj );
		});
	}

	@Override
	public <T> void deleteAll(Class<T> clazz, Session session, String ... args) {
		String query;

		if(args.length == 2)
			query = format("DELETE %s obj WHERE obj.%s = %s", clazz.getSimpleName(), args[0], args[1]);

		else
			query = format("DELETE %s obj WHERE obj.%s = \"%s\" OR obj.%s = \"%s\"",
					clazz.getSimpleName(), args[0], args[1], args[2], args[3]);

		session.createQuery(query, clazz).executeUpdate();
	}

	@Override
	public <T> Result<T> getOne(String id, Class<T> clazz) {
		try (var session = sessionFactory.openSession()) {
			var res = session.find(clazz, id);
			if (res == null)
				return Result.error(ErrorCode.NOT_FOUND);
			else
				return Result.ok(res);
		} catch (Exception e) {
			throw e;
		}
	}

	@Override
	public <T> Result<List<T>> getAllByAttribute(Class<T> clazz, String attribute, String param, String match) {
		String container = clazz.equals(User.class) ? USERS_CONTAINER : SHORTS_CONTAINER;
		var query = format("SELECT obj.%s FROM %s obj WHERE obj.%s = '%s'", attribute, container, param, match);
		return sql(query, clazz);
	}

	@Override
	public <T> Result<List<T>> getAll(Class<T> clazz, String... args) {
		String container = clazz.equals(User.class) ? USERS_CONTAINER : SHORTS_CONTAINER;
		var query = format("SELECT * FROM %s obj WHERE obj.%s = '%s'", container, args[0], args[1]);
		return sql(query, clazz);
	}

	@Override
	public <T> Result<List<T>> sql(String sqlStatement, Class<T> clazz) {
		try (var session = sessionFactory.openSession()) {
			var query = session.createNativeQuery(sqlStatement, clazz);
			return Result.ok(query.list());
		} catch (Exception e) {
			throw e;
		}
	}

	@Override
	public <T> Result<T> execute(Consumer<Session> proc) {
		return execute( (hibernate) -> {
			proc.accept( hibernate);
			return Result.ok();
		});
	}

	@Override
	public <T> Result<T> execute(Function<Session, Result<T>> func) {
		Transaction tx = null;
		// Declare session outside try-with-resources
		try (Session session = sessionFactory.openSession()) {
			// Open session here
			tx = session.beginTransaction();
			var res = func.apply(session);
			tx.commit();
			return res;
		} catch (ConstraintViolationException __) {
			if (tx != null) tx.rollback();
			return Result.error(ErrorCode.CONFLICT);
		} catch (Exception e) {
			if (tx != null) tx.rollback();
			e.printStackTrace();
			throw e;  // Rethrow exception to let the caller handle it
		}
		// Close the session in the final block
	}

}