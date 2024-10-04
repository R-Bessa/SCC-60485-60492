package tukano.impl.rest;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.core.Application;
import tukano.impl.Token;


public class TukanoApplication extends Application {
	private Set<Object> singletons = new HashSet<>();
	private static final String TUKANO_SECRET = "tukano_app_secret";

	public static final String BASE_URI = "https://scc-60492-60485.azurewebsites.net/rest";
	//public static final String BASE_URI = "https://scc-project-60485.azurewebsites.net/rest";

	public TukanoApplication() {
		singletons.add( new RestUsersResource());
		singletons.add( new RestBlobsResource());
		singletons.add( new RestShortsResource());

		Token.setSecret(TUKANO_SECRET);
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}
}