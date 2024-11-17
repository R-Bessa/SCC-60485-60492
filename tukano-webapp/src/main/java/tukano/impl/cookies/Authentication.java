package tukano.impl.cookies;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import tukano.impl.cookies.auth.RequestCookies;
import tukano.impl.storage.cache.RedisCache;

import java.util.UUID;

public class Authentication {
	static final String COOKIE_KEY = "scc:session";
	private static final int MAX_COOKIE_AGE = 3600;

	public static Response login(String user) {
		String uid = UUID.randomUUID().toString();
		var cookie = new NewCookie.Builder(COOKIE_KEY)
				.value(uid).path("/")
				.comment("sessionid")
				.maxAge(MAX_COOKIE_AGE)
				.secure(true) //ideally it should be true to only work for https requests
				.httpOnly(true)
				.build();

		RedisCache.putSession(new Session(uid, user)); //TODO - Might fail here

		return Response.ok().cookie(cookie).build();
	}
	
	static public Session validateSession(String userId) throws NotAuthorizedException {
		var cookies = RequestCookies.get();
		cookies.keySet().forEach(x -> System.out.println(x + " GORDAAAAAAAAAAAA"));
		cookies.values().forEach(x -> System.out.println(x + " OLAAAAAAAAAAAAAAAAA"));
		System.out.println(cookies.get(COOKIE_KEY) + " COOOOOOOOOOOOOOOOOOOOOOKIES");
		return validateSession( cookies.get(COOKIE_KEY ), userId );
	}
	
	static public Session validateSession(Cookie cookie, String userId) throws NotAuthorizedException {

		if (cookie == null ) {
			System.out.println("No session initialized");
			throw new NotAuthorizedException("No session initialized");
		}

		var session = RedisCache.getSession( cookie.getValue());
		if( session == null ) {
			System.out.println("No valid session initialized");
			throw new NotAuthorizedException("No valid session initialized");
		}
			
		if (session.getUser() == null || session.getUser().isEmpty()) {
			System.out.println("No valid session initialized");
			throw new NotAuthorizedException("No valid session initialized");
		}

		if (!session.getUser().equals(userId)) {
			System.out.println("Invalid session initialized");
			throw new NotAuthorizedException("Invalid user : " + session.getUser());
		}

		return session;
	}
}
