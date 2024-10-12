package tukano.impl.rest;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.core.Application;
import tukano.impl.Token;

/** Cloud Version of Tukano */
public class TukanoApplication extends Application {
	private Set<Object> singletons = new HashSet<>();

	public static final String TUKANO_SECRET = "tukano_app_secret";
	public static final long MAX_TOKEN_AGE = 1000000;
	public static final boolean AZURE_BLOBS = true;
	public static final boolean COSMOS_DB = true;


	/** Service Base Uri */

	//public static final String BASE_URI = "https://scc-60485-60492.azurewebsites.net/rest";
	public static final String BASE_URI = "https://scc-project-60485.azurewebsites.net/rest";


	/** Blobs Configs */

	//public static final String BLOB_STORAGE_KEY = "DefaultEndpointsProtocol=https;AccountName=scc60492;AccountKey=2lddvpV/kKYzpiUq6yOzg52AyB599d1OyeJQf694VGMrr0UbRjIj6Rp3Ns/bsm7htNWCmmwkcDSl+AStQ1GPyg==;EndpointSuffix=core.windows.net";
	public static final String BLOB_STORAGE_KEY = "DefaultEndpointsProtocol=https;AccountName=scc60485;AccountKey=tRBfHsTj0Fe+vayowI6sGxu24UuVGf1rjY1p9OIL+0jMOP+P6DKzdXX7XSfbNapuL/2ygbMTRxpF+AStL9Ho9A==;EndpointSuffix=core.windows.net";

	/** DB Configs */

	public static final String CONNECTION_URL = "https://scc-60485.documents.azure.com:443/";
	public static final String DB_KEY = "bpkeX8gaCxgOjRIt8lFRnP0vWJkghppZj3RGUk8EG9CNllTsj7DtpMea6KxJgBVxniQoPDoI6m4XACDbJknRiw==";

	//public static final String CONNECTION_URL = "https://scc-60485-60492.documents.azure.com:443/";
	//public static final String DB_KEY = "gZGjVKxBMJF8fSwF2s3UBmsfdSk9k1vOZq6ziCkCBBsEJYx9wBr1ZRH4tncG5YYh5fW3hoDv0nSdACDbosz4Fg==";



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