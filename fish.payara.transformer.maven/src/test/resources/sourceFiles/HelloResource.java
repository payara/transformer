package rest;

import cdi.*;
import javax.inject.*;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

@Path("hello")
public class HelloResource {

    @Inject
    private CDIBean bean;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getMessage() {
        return bean.getGreetings();
    }
}
