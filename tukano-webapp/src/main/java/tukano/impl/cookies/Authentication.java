package tukano.impl.cookies;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import tukano.impl.JavaShorts;
import tukano.impl.cookies.auth.RequestCookies;
import tukano.impl.storage.cache.RedisCache;

import java.util.UUID;

@Path(Authentication.PATH)
public class Authentication {
	static final String PATH = "login";
	static final String COOKIE_KEY = "scc:session";
	private static final int MAX_COOKIE_AGE = 3600;
	private static final String USER_ID = "userId";
	private static final String PWD = "pwd";

	@POST
	@Path("/{" + USER_ID+ "}")
	public static Response login(@PathParam(USER_ID) String user, @QueryParam(PWD) String pwd) {
		var res = JavaShorts.okUser(user, pwd);
		if(!res.isOK())
			throw new NotAuthorizedException("Incorrect login");
		else {
			String uid = UUID.randomUUID().toString();
			var cookie = new NewCookie.Builder(COOKIE_KEY)
					.value(uid).path("/")
					.comment("sessionid")
					.maxAge(MAX_COOKIE_AGE)
					.secure(false) //ideally it should be true to only work for https requests
					.httpOnly(true)
					.build();


			RedisCache.putSession( new Session( uid, user));


			return Response.ok()
					.cookie(cookie)
					.build();
		}
	}

	static public Session validateSession(String userId) throws NotAuthorizedException {
		var cookies = RequestCookies.get();
		return validateSession( cookies.get(COOKIE_KEY ), userId );
	}

	static public Session validateSession(Cookie cookie, String userId) throws NotAuthorizedException {

		if (cookie == null )
			throw new NotAuthorizedException("No session initialized");

		var session = RedisCache.getSession( cookie.getValue());

		if( session == null )
			throw new NotAuthorizedException("No valid session initialized");

		if (session.getUser() == null || session.getUser().isEmpty())
			throw new NotAuthorizedException("No valid session initialized");

		if (!session.getUser().equals(userId))
			throw new NotAuthorizedException("Invalid user : " + session.getUser());

		return session;
	}
}