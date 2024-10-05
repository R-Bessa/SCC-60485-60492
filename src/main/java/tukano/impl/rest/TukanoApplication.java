package tukano.impl.rest;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import jakarta.ws.rs.core.Application;
import tukano.impl.Token;


public class TukanoApplication extends Application {
	private Set<Object> singletons = new HashSet<>();

	public static final String TUKANO_SECRET = "tukano_app_secret";
	public static final long MAX_TOKEN_AGE = 1000000;
	public static final boolean AZURE_BLOBS = true;


	// public static final String BASE_URI = "https://scc-60485-60492.azurewebsites.net/rest";
	public static final String BASE_URI = "https://scc-project-60485.azurewebsites.net/rest";


	// public static final String BLOB_STORAGE_KEY = "DefaultEndpointsProtocol=https;AccountName=scc60492;AccountKey=2lddvpV/kKYzpiUq6yOzg52AyB599d1OyeJQf694VGMrr0UbRjIj6Rp3Ns/bsm7htNWCmmwkcDSl+AStQ1GPyg==;EndpointSuffix=core.windows.net";
	public static final String BLOB_STORAGE_KEY = "DefaultEndpointsProtocol=https;AccountName=scc60485;AccountKey=tRBfHsTj0Fe+vayowI6sGxu24UuVGf1rjY1p9OIL+0jMOP+P6DKzdXX7XSfbNapuL/2ygbMTRxpF+AStL9Ho9A==;EndpointSuffix=core.windows.net";


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