package test;

import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Random;

import tukano.api.Result;
import tukano.impl.data.User;
import tukano.clients.rest.RestBlobsClient;
import tukano.clients.rest.RestShortsClient;
import tukano.clients.rest.RestUsersClient;
import tukano.impl.data.Short;

public class Test {
	
	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}
	
	public static void main(String[] args ) throws Exception {
		/*new Thread( () -> {
			try { 
				TukanoRestServer.main( new String[] {} );
			} catch( Exception x ) {
				x.printStackTrace();
			}
		}).start();

		
		Thread.sleep(1000);
		
		var serverURI = String.format("http://localhost:%s/rest", TukanoRestServer.PORT);
		
		var blobs = new RestBlobsClient(serverURI);
		var users = new RestUsersClient( serverURI);
		var shorts = new RestShortsClient(serverURI);

		runTester(blobs, users, shorts);
		//runBlobTester(blobs, users, shorts);*/

		System.exit(0);
	}
	
	
	private static Result<?> show( Result<?> res ) {
		if( res.isOK() )
			System.err.println("OK: " + res.value() );
		else
			System.err.println("ERROR:" + res.error());
		return res;
		
	}
	
	private static byte[] randomBytes(int size) {
		var r = new Random(1L);

		var bb = ByteBuffer.allocate(size);
		
		r.ints(size).forEach( i -> bb.put( (byte)(i & 0xFF)));		

		return bb.array();
		
	}

	private static void runTester(RestBlobsClient blobs, RestUsersClient users, RestShortsClient shorts) {
		show(users.createUser( new User("wales", "12345", "jimmy@wikipedia.pt", "Jimmy Wales") ));

		show(users.createUser( new User("liskov", "54321", "liskov@mit.edu", "Barbara Liskov") ));

		show(users.updateUser("wales", "12345", new User("wales", "12345", "jimmy@wikipedia.com", "" ) ));


		show(users.searchUsers(""));


		Result<Short> s1, s2;

		show(s2 = shorts.createShort("liskov", "54321"));
		show(s1 = shorts.createShort("wales", "12345"));
		show(shorts.createShort("wales", "12345"));
		show(shorts.createShort("wales", "12345"));
		show(shorts.createShort("wales", "12345"));

		var blobUrl = URI.create(s2.value().getBlobUrl());
		System.out.println( "------->" + blobUrl );

		var blobId = new File( blobUrl.getPath() ).getName();
		System.out.println( "BlobID:" + blobId );

		var token = blobUrl.getQuery().split("=")[1];

		blobs.upload(blobUrl.toString(), randomBytes( 100 ), token);


		var s2id = s2.value().getShortId();

		show(shorts.follow("liskov", "wales", true, "54321"));
		show(shorts.followers("wales", "12345"));

		show(shorts.like(s2id, "liskov", true, "54321"));
		show(shorts.like(s2id, "liskov", true, "54321"));
		show(shorts.likes(s2id , "54321"));
		show(shorts.getFeed("liskov", "12345"));
		show(shorts.getShort( s2id ));

		show(shorts.getShorts( "wales" ));

		show(shorts.followers("wales", "12345"));

		show(shorts.getFeed("liskov", "12345"));

		show(shorts.getShort( s2id ));

		blobs.download(blobUrl.toString(), token);

		show(users.deleteUser("wales", "12345"));
	}

	private static void runBlobTester(RestBlobsClient blobs, RestUsersClient users, RestShortsClient shorts) throws InterruptedException {
		show(users.createUser( new User("liskov", "54321", "liskov@mit.edu", "Barbara Liskov") ));
		show(users.createUser( new User("wales", "12345", "jimmy@wikipedia.pt", "Jimmy Wales") ));
		show(shorts.follow("wales", "liskov", true, "12345"));

		Result<Short> s1, s2;
		show(s1 = shorts.createShort("liskov", "54321"));
		show(s2 = shorts.createShort("liskov", "54321"));

		var shortID1 = s1.value().getShortId();
		var shortID2 = s2.value().getShortId();

		var blobUrl1 = URI.create(s1.value().getBlobUrl());
		var blobUrl2 = URI.create(s1.value().getBlobUrl());

		var token1 = blobUrl1.getQuery().split("=")[1];
		var token2 = blobUrl2.getQuery().split("=")[1];


		// Try to download from an unknown blob
		show(blobs.download(blobUrl1.toString(), token1));

		// Try to upload to an unknown blob
		show(blobs.upload(blobUrl1 + "xx", randomBytes( 100 ), token1));

		show(blobs.upload(blobUrl1.toString(), randomBytes( 100 ), token1));
		show(shorts.like(shortID1, "liskov", true, "54321"));

		show(blobs.upload(blobUrl2.toString(), randomBytes( 100 ), token2));
		show(shorts.like(shortID2, "liskov", true, "54321"));


		// Try to upload to an already existing blob
		//show(blobs.upload(blobUrl1.toString(), randomBytes( 100 ), token1));

		show(blobs.download(blobUrl1.toString(), token1));
		show(blobs.download(blobUrl2.toString(), token2));

		// Try to download a deleted short video
		//show(shorts.deleteShort(s1.value().getShortId(), "54321"));
		//show(blobs.download(blobUrl1.toString(), token1));

		// Try to download video from deleted user
		//users.deleteUser("liskov", "54321");
		//show(blobs.download(blobUrl2.toString(), token2));


	}
}
