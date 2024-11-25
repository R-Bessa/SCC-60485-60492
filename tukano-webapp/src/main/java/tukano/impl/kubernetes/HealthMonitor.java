package tukano.impl.kubernetes;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path(HealthMonitor.PATH)
public class HealthMonitor {
    static final String PATH = "health";

    @GET
    public Response checkHealth() {
        return Response.ok("Application is healthy!").build();
    }
}
