package tukano.impl.rest;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.core.Application;
import tukano.impl.JavaUsers;
import tukano.impl.Token;
import tukano.impl.cookies.Authentication;
import tukano.impl.cookies.auth.RequestCookiesCleanupFilter;
import tukano.impl.cookies.auth.RequestCookiesFilter;
import tukano.impl.data.User;
import tukano.impl.georeplication.Region;
import tukano.impl.storage.blobs.BlobsType;
import tukano.impl.storage.db.DatabaseType;

/** Cloud Version of Tukano */

public class TukanoApplication extends Application {
	private Set<Object> singletons = new HashSet<>();

	public static final String TUKANO_SECRET = "tukano-secret";
	public static final String TUKANO_RECOMMENDS = "tukano";


	/** Service Base Uri  */

	//public static final String PRIMARY_BASE_URI = "https://scc-project-60485.azurewebsites.net/rest";
	public static final String PRIMARY_BASE_URI = "https://scc-60485-60492.azurewebsites.net/rest";
	public static final String SECONDARY_BASE_URI = "https://scc-60485-60492-us.azurewebsites.net/rest";
	public static final Region CURRENT_REGION = Region.WEST_EUROPE;


	/** Blobs Configs */

	public static final boolean BLOBS_GEO_REPLICATION = false;
	public static final BlobsType BLOBS_TYPE = BlobsType.AZURE_BLOBS;
	public static final long MAX_TOKEN_AGE = 300000;
	public static final String BLOB_STORAGE_KEY = "DefaultEndpointsProtocol=https;AccountName=scc60492;AccountKey=HwhiZRDl0MQcOy2sSzWJ3ZNYNVGnVu2ff9sVlp4l/3trXW2jLVnD6sU8QgBrH7rrChHsWxNpzvSf+AStA+Ln1g==;EndpointSuffix=core.windows.net";
	public static final String SECONDARY_BLOB_STORAGE_KEY = "";

	/** DB Configs */

	public static final DatabaseType USERS_DB_TYPE = DatabaseType.HIBERNATE;
	public static final DatabaseType SHORTS_DB_TYPE = DatabaseType.HIBERNATE;
	public static final String CONNECTION_URL = "";
	public static final String DB_KEY = "";


	/** Redis Cache Configs */

	public static final boolean REDIS_CACHE_ON = true;
	public static final String REDIS_HOSTNAME = "scc-60485-60492.redis.cache.windows.net";
	public static final String REDIS_KEY = "natqdRICc9GWhnw2BghDlUfNDBxQomuTAAzCaEmATj4=";


	public TukanoApplication() {
		singletons.add( new RestUsersResource());
		singletons.add( new RestShortsResource());


		if(!BLOBS_TYPE.equals(BlobsType.SERVERLESS_BLOBS))
			singletons.add( new RestBlobsResource());

		Token.setSecret(TUKANO_SECRET);
		JavaUsers.getInstance().createUser(new User(TUKANO_RECOMMENDS, "pwd", "tukano-email", "tukano-recommends"));
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}
}