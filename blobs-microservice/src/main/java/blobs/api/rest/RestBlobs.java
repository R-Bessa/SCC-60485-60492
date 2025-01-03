package blobs.api.rest;


import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path(RestBlobs.PATH)
public interface RestBlobs {
	
	String PATH = "/blobs";
	String BLOB_ID = "blobId";
	String TOKEN = "token";
	String PWD = "pwd";
	String BLOBS = "blobs";
	String USER_ID = "userId";
	String SECRET = "secret";
	String TUKANO = "tukano";

 	@POST
 	@Path("/{" + BLOB_ID +"}")
 	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	void upload(@PathParam(BLOB_ID) String blobId, byte[] bytes, @QueryParam(TOKEN) String token);


 	@GET
 	@Path("/{" + BLOB_ID +"}") 	
 	@Produces(MediaType.APPLICATION_OCTET_STREAM)
 	byte[] download(@PathParam(BLOB_ID) String blobId, @QueryParam(TOKEN) String token);
 	
 	
	@DELETE
	@Path("/{" + BLOB_ID + "}")
	void delete(@PathParam(BLOB_ID) String blobId, @QueryParam(TOKEN) String token );		

	@DELETE
	@Path("/{" + USER_ID + "}/" + BLOBS)
	void deleteAllBlobs(@PathParam(USER_ID) String userId, @QueryParam(PWD) String pwd );

	@DELETE
	@Path("/{" + BLOB_ID + "}/" + SECRET)
	void delete(@PathParam(BLOB_ID) String blobId, @QueryParam(TOKEN) String token, @QueryParam(SECRET) String secret );

	@DELETE
	@Path("/{" + USER_ID + "}/" + BLOBS + "/" + SECRET)
	void deleteAllBlobs(@PathParam(USER_ID) String userId, @QueryParam(PWD) String pwd, @QueryParam(SECRET) String secret );
}
