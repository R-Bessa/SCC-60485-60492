package tukano.impl.rest;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.core.Application;


public class TukanoApplication extends Application {
	private Set<Object> singletons = new HashSet<>();

	public TukanoApplication() {
		singletons.add( new RestUsersResource());
		singletons.add( new RestBlobsResource());
		singletons.add( new RestShortsResource());
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}
}