package net.bull.javamelody.centralizedserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bull.javamelody.Collector;
import net.bull.javamelody.Counter;
import net.bull.javamelody.FilterContext;
import net.bull.javamelody.HttpParameters;
import net.bull.javamelody.I18N;
import net.bull.javamelody.JavaInformations;
import net.bull.javamelody.Parameter;
import net.bull.javamelody.Parameters;
import net.bull.javamelody.Period;
import net.bull.javamelody.Range;

/**
 * 
 * This listener will push the application monitor data to the centralized server when sevlet be initialed.
 * 
 * @author Greg
 *
 */
public class CentralizedServerPushListener implements ServletContextListener {

	/**
	 * Use to specify the delay for waiting server start between 
	 * {@link javax.servlet.ServletContextListener#contextInitialized(ServletContextEvent)} 
	 * and server start time.
	 */
	protected static final int PUSH_DELAYS = 30 * 1000;

	protected static final int PUSH_PERIOD = Parameters.getResolutionSeconds() * 1000;

	protected String appName = "";

	private final static Logger LOGGER = LoggerFactory
			.getLogger(CentralizedServerPushListener.class);

	/** {@inheritDoc} */
	@Override
	public void contextInitialized(ServletContextEvent e) {
		// Init app name
		appName = e.getServletContext().getInitParameter(Parameter.APP_NAME.getCode());
		//Set full app name
		InetAddress i = null;
		try {
			i = InetAddress.getLocalHost();
		} catch (UnknownHostException e1) {
			LOGGER.error("Get private ip failed.", e1);
		}
		String appAddress = i.getHostAddress();
		appName = appName + "-ip:" + appAddress;

		LOGGER.info("CentralizedServePushListener initialzing...");
		ServletContext context = e.getServletContext();
		Timer time = new Timer(); // Instantiate Timer Object
		PushMasterThread pushToCollectionServerTask = new PushMasterThread(context);
		time.schedule(pushToCollectionServerTask, PUSH_DELAYS, PUSH_PERIOD);
	}

	/** {@inheritDoc} */
	@Override
	public void contextDestroyed(ServletContextEvent e) {
		//nothing to be done.
	}

	private class PushMasterThread extends TimerTask {

		private final Logger LOGGER_INNER = LoggerFactory.getLogger(PushMasterThread.class);

		private ServletContext servletContext;

		public PushMasterThread(ServletContext context) {
			this.servletContext = context;
		}

		@Override
		public void run() {
			try {
				JavaInformations javaInformations = new JavaInformations(servletContext, true);
				FilterContext filterContext = new FilterContext();
				final Collector collector = filterContext.getCollector();
				Range range = new Range(Period.TOUT, null, null);
				Serializable serializable = createDefaultSerializable(
						Collections.singletonList(javaInformations), range, collector);
				CloseableHttpClient httpclient = HttpClients.createDefault();
				URIBuilder centralizeServerbuilder = new URIBuilder();
				centralizeServerbuilder.setScheme("http").setHost("localhost")
						.setPort(new Integer("8280"))
						.setPath("/javamelody-collector-server-1.61.0-custom"
								+ Parameters.getDefaultPushingPath());
				System.out.println(
						"_____________" + centralizeServerbuilder.build().toURL().toString());
				LOGGER_INNER
						.info("_____________" + centralizeServerbuilder.build().toURL().toString());
				HttpPost httpPost = new HttpPost(centralizeServerbuilder.build());

				MultipartEntityBuilder builder = MultipartEntityBuilder.create()
						.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
						.setCharset(Charset.forName("UTF-8"));

				final String fileName = "JavaMelody_"
						+ "local_push_su_test".replace(' ', '_').replace("/", "") + '_'
						+ I18N.getCurrentDate().replace('/', '_') + '.'
						+ this.toString().toLowerCase(Locale.ENGLISH);

				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ObjectOutput out = null;
				out = new ObjectOutputStream(bos);
				out.writeObject(serializable);
				byte[] yourBytes = bos.toByteArray();

				builder.addBinaryBody("file", yourBytes, ContentType.APPLICATION_OCTET_STREAM,
						fileName);
				builder.addTextBody(HttpParameters.APPLICATION, "local_push_su_test");

				HttpEntity entity = builder.build();
				httpPost.setEntity(entity);

				CloseableHttpResponse response2 = httpclient.execute(httpPost);
			} catch (Exception e) {
				LOGGER_INNER.error("Push to Javamelody centralized server failed!", e);
			}
		}

		Serializable createDefaultSerializable(List<JavaInformations> javaInformationsList,
				Range range, Collector collector) throws IOException {
			final List<Counter> counters = collector.getRangeCounters(range);
			final List<Serializable> serialized = new ArrayList<>(
					counters.size() + javaInformationsList.size());
			// on clone les counters avant de les sérialiser pour ne pas avoir de problèmes de concurrences d'accès
			for (final Counter counter : counters) {
				serialized.add(counter.clone());
			}
			serialized.addAll(javaInformationsList);
			return (Serializable) serialized;
		}
	}

}
