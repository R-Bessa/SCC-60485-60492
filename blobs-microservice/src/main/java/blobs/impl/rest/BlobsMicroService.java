package blobs.impl.rest;

import jakarta.ws.rs.core.Application;
import blobs.impl.Token;
import blobs.impl.cookies.Authentication;
import blobs.impl.cookies.auth.RequestCookiesCleanupFilter;
import blobs.impl.cookies.auth.RequestCookiesFilter;
import blobs.impl.kubernetes.HealthMonitor;
import blobs.impl.storage.blobs.BlobsType;
import blobs.impl.storage.cache.RedisCache;

import java.util.HashSet;
import java.util.Set;

/** Cloud Version of Tukano */

public class BlobsMicroService extends Application {
	private Set<Object> singletons = new HashSet<>();
	private Set<Class<?>> resources = new HashSet<>();

	public static final String TUKANO_SECRET = "tukano-secret";
	public static final String ADMIN = "admin";

	/** Service Base Uri  */

	public static final String PRIMARY_BASE_URI = "https://scc-project-60485.azurewebsites.net/rest";

	/** Blobs Configs */

	public static final BlobsType BLOBS_TYPE = BlobsType.FILESYSTEM;
	public static final long MAX_TOKEN_AGE = 300000;
	//public static final String BLOB_STORAGE_KEY = "DefaultEndpointsProtocol=https;AccountName=scc60492;AccountKey=HwhiZRDl0MQcOy2sSzWJ3ZNYNVGnVu2ff9sVlp4l/3trXW2jLVnD6sU8QgBrH7rrChHsWxNpzvSf+AStA+Ln1g==;EndpointSuffix=core.windows.net";
	public static final String BLOB_STORAGE_KEY = "DefaultEndpointsProtocol=https;AccountName=scc60485;AccountKey=tRBfHsTj0Fe+vayowI6sGxu24UuVGf1rjY1p9OIL+0jMOP+P6DKzdXX7XSfbNapuL/2ygbMTRxpF+AStL9Ho9A==;EndpointSuffix=core.windows.net";
	public static final String POSTGRES_URL = "jdbc:postgresql://postgres:5432/tukano-db?user=citus&password=Sigma!!!";

	/** Redis Cache Configs */

	public static final boolean REDIS_CACHE_ON = true;
	public static final boolean DOCKERIZED_REDIS = true;
	public static final String REDIS_HOSTNAME = "cache"; // TODO put as env
	public static final String REDIS_KEY = "cachePwd"; // TODO put as env

	public BlobsMicroService() {

		resources.add(HealthMonitor.class);
		resources.add(Authentication.class);
		resources.add(RequestCookiesCleanupFilter.class);
		resources.add(RequestCookiesFilter.class);

		singletons.add( new RestBlobsResource());

		if(REDIS_CACHE_ON)
			RedisCache.init();

		Token.setSecret(TUKANO_SECRET);
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