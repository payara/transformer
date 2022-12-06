package rest;
import javax.ws.rs.Path;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.inject.Inject;
import cdi.CDIBean;

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
