package org.eclipse.transformer.maven;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("echo")
public class EchoService {

	@POST
	@Produces("text/plain")
	@Consumes("text/plain")
	public String echo(final String input) {
		return input;
	}

}
