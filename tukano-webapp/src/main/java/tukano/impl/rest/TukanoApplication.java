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
import tukano.impl.kubernetes.HealthMonitor;
import tukano.impl.storage.blobs.BlobsType;
import tukano.impl.storage.cache.RedisCache;
import tukano.impl.storage.db.DatabaseType;

/** Cloud Version of Tukano */

public class TukanoApplication extends Application {
	private Set<Object> singletons = new HashSet<>();
	private Set<Class<?>> resources = new HashSet<>();

	//public static final String TUKANO_SECRET = System.getenv("TUKANO_SECRET");
	public static final String TUKANO_SECRET = "tukano-secret";
	public static final String TUKANO_RECOMMENDS = "tukano";
	public static final String ADMIN = "admin";

	/** Service Base Uri  */

	public static final String PRIMARY_BASE_URI = "https://scc-project-60485.azurewebsites.net/rest";
	//public static final String PRIMARY_BASE_URI = "https://scc-60485-60492.azurewebsites.net/rest";
	public static final String SECONDARY_BASE_URI = "https://scc-60485-60492-us.azurewebsites.net/rest";
	public static final Region CURRENT_REGION = Region.WEST_EUROPE;


	/** Blobs Configs */

	public static final boolean BLOBS_GEO_REPLICATION = false;
	public static final BlobsType BLOBS_TYPE = BlobsType.AZURE_BLOBS;
	public static final long MAX_TOKEN_AGE = 300000;
	//public static final String BLOB_STORAGE_KEY = "DefaultEndpointsProtocol=https;AccountName=scc60492;AccountKey=HwhiZRDl0MQcOy2sSzWJ3ZNYNVGnVu2ff9sVlp4l/3trXW2jLVnD6sU8QgBrH7rrChHsWxNpzvSf+AStA+Ln1g==;EndpointSuffix=core.windows.net";
	public static final String BLOB_STORAGE_KEY = "DefaultEndpointsProtocol=https;AccountName=scc60485;AccountKey=tRBfHsTj0Fe+vayowI6sGxu24UuVGf1rjY1p9OIL+0jMOP+P6DKzdXX7XSfbNapuL/2ygbMTRxpF+AStL9Ho9A==;EndpointSuffix=core.windows.net";
	public static final String SECONDARY_BLOB_STORAGE_KEY = "";

	/** DB Configs */

	public static final boolean DOCKER_POSTGRES_ON = true;
	public static final DatabaseType USERS_DB_TYPE = DatabaseType.COSMOS_DB_POSTGRESQL;
	public static final DatabaseType SHORTS_DB_TYPE = DatabaseType.COSMOS_DB_POSTGRESQL;
	public static final String CONNECTION_URL = "";
	public static final String DB_KEY = "";


	/** Redis Cache Configs */

	public static final boolean REDIS_CACHE_ON = true;
	public static final boolean DOCKERIZED_REDIS = true;
	//public static final String REDIS_HOSTNAME = System.getenv("REDIS_HOSTNAME");
	//public static final String REDIS_KEY = System.getenv("CACHE_PWD");
	public static final String REDIS_HOSTNAME = "cache"; //TODO name of kubernetes service or container
	public static final String REDIS_KEY = "cachePwd"; //TODO send as env to kubernetes

	public TukanoApplication() {
		singletons.add( new RestUsersResource());
		singletons.add( new RestShortsResource());

		resources.add(RequestCookiesFilter.class);
		resources.add(RequestCookiesCleanupFilter.class);
		resources.add(Authentication.class);
		resources.add(HealthMonitor.class);


		if(!BLOBS_TYPE.equals(BlobsType.SERVERLESS_BLOBS))
			singletons.add( new RestBlobsResource());

		if(REDIS_CACHE_ON)
			RedisCache.init();

		Token.setSecret(TUKANO_SECRET);
		JavaUsers.getInstance().createUser(new User(TUKANO_RECOMMENDS, "pwd", "tukano-email", "tukano-recommends"));
		JavaUsers.getInstance().createUser(new User(ADMIN, "pwd", "admin-email", "admin"));

	}

	@Override
	public Set<Class<?>> getClasses() {
		return resources;
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}
}