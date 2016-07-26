package net.bull.javamelody.centralizedserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bull.javamelody.Collector;
import net.bull.javamelody.FilterContext;
import net.bull.javamelody.MonitoringController;
import net.bull.javamelody.Parameter;
import net.bull.javamelody.Parameters;

/**
 * 
 * This listener will push the application monitor data to the centralized server when sevlet be initialed.
 * 
 * @author Greg
 *
 */
public class CentralizedServerPushListener implements ServletContextListener {

	protected String centralizedServerAddress = "";

	protected String centralizedServerPort = "80";

	protected String centralizedServerContextPath = "";

	protected boolean usePublicIp;

	protected String appAddress = "";

	protected String appPort = "80";

	protected String appName = "";

	protected String appContextPath = "";

	//Make sure only init once.
	private static boolean isInitialized = false;

	/**
	 * Use to specify the delay for waiting server start between 
	 * {@link javax.servlet.ServletContextListener#contextInitialized(ServletContextEvent)} 
	 * and server start time.
	 */
	protected static final int PUSH_DELAYS = 30 * 1000;

	protected static final int PUSH_PERIOD = Parameters.getResolutionSeconds() * 1000;

	private static final String IP_WEB_SERVICE_URL = Parameters.getIpWebserviceUrl();

	private final static Logger LOGGER = LoggerFactory
			.getLogger(CentralizedServerPushListener.class);

	/** {@inheritDoc} */
	@Override
	public void contextInitialized(ServletContextEvent e) {
		initPushToCollectionServerTask(e);
	}

	/** {@inheritDoc} */
	@Override
	public void contextDestroyed(ServletContextEvent e) {
		//nothing to be done.
	}

	private void initPushToCollectionServerTask(ServletContextEvent e) {
		if (isInitialized) {
			return;
		}
		initializeParameters(e);

		LOGGER.info("-------------------------------------------");
		LOGGER.info("Javamelody PushToCollectionServerTask initialzing...");
		LOGGER.info("Context path: " + e.getServletContext().getContextPath());
		LOGGER.info("Context server info: " + e.getServletContext().getServerInfo());
		LOGGER.info("Context virtual server info: " + e.getServletContext().getVirtualServerName());
		LOGGER.info("-------------------------------------------");

		ServletContext context = e.getServletContext();
		Timer time = new Timer(); // Instantiate Timer Object
		PushMasterThread pushToCollectionServerTask = new PushMasterThread(context);
		time.schedule(pushToCollectionServerTask, PUSH_DELAYS, PUSH_PERIOD);
		isInitialized = true;
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

	private class PushMasterThread extends TimerTask {

		private final Logger LOGGER_INNER = LoggerFactory.getLogger(PushMasterThread.class);

		private ServletContext servletContext;

		public PushMasterThread(ServletContext context) {
			Date currentTime = new Date();
			LOGGER_INNER.info("PushMasterThread constructed, time: " + currentTime);
			this.servletContext = context;
		}

		@Override
		public void run() {
			try {
				FilterContext filterContext = FilterContext.getFilterContextSingleton();
				final Collector collector = filterContext.getCollector();
				final MonitoringController monitoringController = new MonitoringController(
						collector, null);

				//Set full app name
				String fullAppName = appName + "-ip:" + appAddress;

				URIBuilder centralizeServerbuilder = new URIBuilder();
				centralizeServerbuilder.setScheme("http").setHost(centralizedServerAddress)
						.setPort(new Integer(centralizedServerPort))
						.setPath(centralizedServerContextPath + Parameters.getPushingPath());

				if (centralizedServerPort != null && !centralizedServerPort.equals("")) {
					centralizeServerbuilder.setPort(new Integer(centralizedServerPort));
				}

				monitoringController.doActionCollectDataForPush(servletContext,
						centralizeServerbuilder.build(), fullAppName);
			} catch (Exception e) {
				LOGGER_INNER.error("Push to Javamelody centralized server failed!", e);
			}
		}
	}

}
