package net.bull.javamelody;

import java.io.FileNotFoundException;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

/**
 * @author greg
 *
 */
@MultipartConfig
@WebServlet(name = "PushServlet", urlPatterns = { "/psuhing_app_data" })
public class PushServlet extends HttpServlet {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4369675091662692087L;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		req.setCharacterEncoding("UTF-8");
		for (Part part : req.getParts()) {
			System.out.println(part.getName());
		}
	}

	private void write(Part part) throws IOException, FileNotFoundException {
		String header = part.getHeader("Content-Disposition");
		String filename = header.substring(header.indexOf("filename=\"") + 10,
				header.lastIndexOf("\""));
		part.write(filename);
	}
}
