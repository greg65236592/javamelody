package net.bull.javamelody.centralizedserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bull.javamelody.Parameter;
import net.bull.javamelody.Parameters;

/**
 * 
 * This listener will notify the centralized server when sevlet be initialed.
 * 
 * @author Greg
 *
 */
public class CentralizedServerNotificationListener implements ServletContextListener {

	protected String centralizedServerAddress = "";

	protected String centralizedServerPort = "80";

	protected String centralizedServerContextPath = "";

	protected boolean usePublicIp;

	protected String appAddress = "";

	protected String appPort = "80";

	protected String appName = "";

	protected String appContextPath = "";

	/**
	 * Use to specify the delay for waiting server start between 
	 * {@link javax.servlet.ServletContextListener#contextInitialized(ServletContextEvent)} 
	 * and server start time.
	 */
	protected static final int NOTIFY_DELAYS = 30 * 1000;

	protected static final int REGISTER_PERIOD = 30 * 1000;

	private static final String IP_WEB_SERVICE_URL = Parameters.getIpWebserviceUrl();

	private final static Logger LOGGER = LoggerFactory
			.getLogger(CentralizedServerNotificationListener.class);

	/** {@inheritDoc} */
	@Override
	public void contextInitialized(ServletContextEvent e) {
		LOGGER.info("CentralizedServerNotificationListener initialzing...");
		initializeParameters(e);
		Timer time = new Timer(); // Instantiate Timer Object
		CallMasterThread callCollectionServerTask = new CallMasterThread();
		time.schedule(callCollectionServerTask, NOTIFY_DELAYS, REGISTER_PERIOD);
	}

	/** {@inheritDoc} */
	@Override
	public void contextDestroyed(ServletContextEvent e) {
		//nothing to be done.
	}

	private void initializeParameters(ServletContextEvent e) {
		// Init centralized server host
		centralizedServerAddress = e.getServletContext()
				.getInitParameter(Parameter.CENTRALIZED_SERVER_HOST.getCode());
		// Init centralized server port
		if (e.getServletContext()
				.getInitParameter(Parameter.CENTRALIZED_SERVER_PORT.getCode()) != null) {
			centralizedServerPort = e.getServletContext()
					.getInitParameter(Parameter.CENTRALIZED_SERVER_PORT.getCode());
		}
		// Init centralized server context path
		if (e.getServletContext()
				.getInitParameter(Parameter.CENTRALIZED_SERVER_CONTEXT_PATH.getCode()) != null) {
			centralizedServerContextPath = e.getServletContext()
					.getInitParameter(Parameter.CENTRALIZED_SERVER_CONTEXT_PATH.getCode());
		}
		// Init app name
		appName = e.getServletContext().getInitParameter(Parameter.APP_NAME.getCode());
		// Init app port
		if (e.getServletContext().getInitParameter(Parameter.APP_PORT.getCode()) != null) {
			appPort = e.getServletContext().getInitParameter(Parameter.APP_PORT.getCode());
		}
		// Init app context path
		appContextPath = e.getServletContext().getContextPath();
		// Init app host
		if (e.getServletContext().getInitParameter(Parameter.USE_PUBLIC_IP.getCode()) != null) {
			usePublicIp = new Boolean(
					e.getServletContext().getInitParameter(Parameter.USE_PUBLIC_IP.getCode()));
		}
		if (e.getServletContext().getInitParameter(Parameter.APP_HOST.getCode()) != null) {
			appAddress = e.getServletContext().getInitParameter(Parameter.APP_HOST.getCode());
		}
		if (usePublicIp) {
			try {
				URL whatismyip = new URL(IP_WEB_SERVICE_URL);
				BufferedReader in = new BufferedReader(
						new InputStreamReader(whatismyip.openStream()));
				appAddress = in.readLine();
			} catch (IOException e1) {
				LOGGER.error("Get instance public ip failed!", e1);
			}
		} else if (!usePublicIp && appAddress == "") {
			try {
				InetAddress i = InetAddress.getLocalHost();
				appAddress = i.getHostAddress();
			} catch (UnknownHostException e1) {
				LOGGER.error("Get instance internal ip failed!", e1);
			}
		}
	}

	private class CallMasterThread extends TimerTask {

		private final Logger LOGGER_INNER = LoggerFactory.getLogger(CallMasterThread.class);

		public CallMasterThread() {
		}

		@Override
		public void run() {
			try {
				callMasterServer();
			} catch (Exception e) {
				LOGGER_INNER.error("Call Javamelody centralized server failed!", e);
			}
		}

		private void callMasterServer()
				throws ClientProtocolException, IOException, URISyntaxException {

			CloseableHttpClient httpclient = HttpClients.createDefault();

			URIBuilder centralizeServerbuilder = new URIBuilder();
			URIBuilder appFullAddressbuilder = new URIBuilder();
			//Set full app name
			String fullAppName = appName + "-ip:" + appAddress;
			//Set full app url
			appFullAddressbuilder.setScheme("http").setHost(appAddress)
					.setPort(new Integer(appPort)).setPath(appContextPath);
			URI fullAppUrl = appFullAddressbuilder.build();
			//Remove invalid character
			fullAppName.replace(" ", "");

			centralizeServerbuilder.setScheme("http").setHost(centralizedServerAddress)
					.setPort(new Integer(centralizedServerPort))
					.setPath(centralizedServerContextPath).setParameter("appName", fullAppName)
					.setParameter("appUrls", fullAppUrl.toString());
			if (centralizedServerPort != null && !centralizedServerPort.equals("")) {
				centralizeServerbuilder.setPort(new Integer(centralizedServerPort));
			}

			HttpPost httpPost = new HttpPost(centralizeServerbuilder.build());

			//Display info
			LOGGER_INNER.info("Registering to javamelody centralized server, instance app name: "
					+ fullAppName);
			LOGGER_INNER.info("Connect to javamelody centralized server, full request: "
					+ centralizeServerbuilder.build());
			LOGGER_INNER.info("Registering to javamelody centralized server, instance app url: "
					+ fullAppUrl.toString());

			CloseableHttpResponse response2 = httpclient.execute(httpPost);

			try {
				LOGGER_INNER.info(response2.getStatusLine().toString());
				HttpEntity entity2 = response2.getEntity();
				LOGGER_INNER.info(getStringFromInputStream(entity2.getContent()));
				// do something useful with the response body
				// and ensure it is fully consumed
				EntityUtils.consume(entity2);
			} finally {
				response2.close();
			}

		}

	}

	// convert InputStream to String
	protected static String getStringFromInputStream(InputStream is) {

		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();

		String line;
		try {

			br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return sb.toString();

	}

}
