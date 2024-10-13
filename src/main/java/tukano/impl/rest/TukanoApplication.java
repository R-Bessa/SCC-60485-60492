package tukano.impl.rest;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.core.Application;
import tukano.impl.Token;
import tukano.impl.storage.blobs.BlobsType;
import tukano.impl.storage.db.DatabaseType;

/** Cloud Version of Tukano */
public class TukanoApplication extends Application {
	private Set<Object> singletons = new HashSet<>();

	public static final String TUKANO_SECRET = "tukano_app_secret";
	public static final long MAX_TOKEN_AGE = 1000000;
	public static final BlobsType BLOBS_TYPE = BlobsType.AZURE_BLOBS;
	public static final DatabaseType USERS_DB_TYPE = DatabaseType.COSMOS_DB_NOSQL;
	public static final DatabaseType SHORTS_DB_TYPE = DatabaseType.COSMOS_DB_NOSQL;


	/** Service Base Uri */

	//public static final String BASE_URI = "https://scc-60485-60492.azurewebsites.net/rest";
	public static final String BASE_URI = "https://scc-project-60485.azurewebsites.net/rest";


	/** Blobs Configs */

	//public static final String BLOB_STORAGE_KEY = "DefaultEndpointsProtocol=https;AccountName=scc60492;AccountKey=2lddvpV/kKYzpiUq6yOzg52AyB599d1OyeJQf694VGMrr0UbRjIj6Rp3Ns/bsm7htNWCmmwkcDSl+AStQ1GPyg==;EndpointSuffix=core.windows.net";
	public static final String BLOB_STORAGE_KEY = "DefaultEndpointsProtocol=https;AccountName=scc60485;AccountKey=tRBfHsTj0Fe+vayowI6sGxu24UuVGf1rjY1p9OIL+0jMOP+P6DKzdXX7XSfbNapuL/2ygbMTRxpF+AStL9Ho9A==;EndpointSuffix=core.windows.net";

	/** DB Configs */

	public static final String CONNECTION_URL = "https://scc-60485.documents.azure.com:443/";
	public static final String DB_KEY = "xC8qPrp6TGvkpNKyOU1fmaGh0vC7Hdgawfi2UvfPbbDrRxfnGhI8aJjKmkZn8Qob9ChpHdGEcC4LACDb9tdq0A==";

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