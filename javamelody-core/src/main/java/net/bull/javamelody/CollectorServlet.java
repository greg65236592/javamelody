/*
 * Copyright 2008-2016 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.bull.javamelody; // NOPMD

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Servlet de collecte utilisée uniquement pour serveur de collecte séparé de l'application monitorée.
 * @author Emeric Vernat
 */
public class CollectorServlet extends HttpServlet {
	private static final long serialVersionUID = -2070469677921953224L;

	@SuppressWarnings("all")
	private static final Logger LOGGER = Logger.getLogger("javamelody");

	@SuppressWarnings("all")
	private transient HttpAuth httpAuth;

	@SuppressWarnings("all")
	private transient CollectorServer collectorServer;

	/** {@inheritDoc} */
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		LOGGER.info(
				"JavaMelody stroage root path: " + Parameters.getStorageRootDirectory().getPath());

		Parameters.initialize(config.getServletContext());
		if (!Boolean.parseBoolean(Parameters.getParameter(Parameter.LOG))) {
			// si log désactivé dans serveur de collecte,
			// alors pas de log, comme dans webapp
			LOGGER.setLevel(Level.WARN);
		}
		// dans le serveur de collecte, on est sûr que log4j est disponible
		LOGGER.info("initialization of the collector servlet of the monitoring");

		httpAuth = new HttpAuth();

		try {
			collectorServer = new CollectorServer();
		} catch (final IOException e) {
			throw new ServletException(e.getMessage(), e);
		}
	}

	/** {@inheritDoc} */
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		if (!httpAuth.isAllowed(req, resp)) {
			return;
		}

		final long start = System.currentTimeMillis();
		final CollectorController collectorController = new CollectorController(collectorServer);
		/*
		 * Get app name
		 */
		String application = collectorController.getApplication(req, resp);
		I18N.bindLocale(req.getLocale());
		try {
			Map<String, List<URL>> appMap = Parameters.getCollectorUrlsByApplications();
			//If application == null, try to get name from current collectors.
			if (application == null) {
				if (appMap != null) {
					Set<Entry<String, List<URL>>> entries = appMap.entrySet();
					for (Entry<String, List<URL>> entry : entries) {
						application = entry.getKey();
					}
				}
			}
			// If no collectors found, and application == null, there will be to application data to be shown.
			if (application == null
					&& Parameters.getCollectorUrlsByApplications().keySet().size() == 0) {
				CollectorController.writeOnlyAddApplication(resp);
				return;
			}
			boolean appCollectorExists = false;
			if (appMap != null) {
				Set<Entry<String, List<URL>>> entries = appMap.entrySet();
				for (Entry<String, List<URL>> entry : entries) {
					String appName = entry.getKey();
					if (appName.equals(application)) {
						appCollectorExists = true;
					}
				}
			}
			// If application != null && no corresponding collectors.
			if (application != null && !appCollectorExists) {
				if (!collectorServer.isApplicationDataAvailable(application)) {
					resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
							"Data unavailable for the application "
									+ I18N.htmlEncode(application, false));
					return;
				}
			}
			collectorController.doMonitoring(req, resp, application);
		} finally {
			I18N.unbindLocale();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("monitoring from " + req.getRemoteAddr() + ", request="
						+ req.getRequestURI()
						+ (req.getQueryString() != null ? '?' + req.getQueryString() : "")
						+ ", application=" + application + " in "
						+ (System.currentTimeMillis() - start) + "ms");
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		if (!httpAuth.isAllowed(req, resp)) {
			return;
		}

		// post du formulaire d'ajout d'application à monitorer
		final String appName = req.getParameter(Parameter.REQ_PARA_APPNAME.getCode());
		final String appUrls = req.getParameter(Parameter.REQ_PARA_APPURLS.getCode());
		I18N.bindLocale(req.getLocale());
		final CollectorController collectorController = new CollectorController(collectorServer);
		try {

			// If push data from applications
			String queryString = req.getRequestURI().replace(req.getContextPath(), "");
			if (queryString != null && queryString.equals(Parameters.getPushingPath())) {
				collectorController.pushApplicationData(req, resp);
			}

			if (appName == null || appUrls == null) {
				writeMessage(req, resp, collectorController, I18N.getString("donnees_manquantes"));
				return;
			}
			if (!appUrls.startsWith("http://") && !appUrls.startsWith("https://")) {
				writeMessage(req, resp, collectorController, I18N.getString("urls_format"));
				return;
			}
			collectorController.addCollectorApplication(appName, appUrls);
			LOGGER.info("monitored application added: " + appName);
			LOGGER.info("urls of the monitored application: " + appUrls);
			CollectorController.showAlertAndRedirectTo(resp,
					I18N.getFormattedString("application_ajoutee", appName),
					"?application=" + appName);
		} catch (final FileNotFoundException e) {
			final String message = I18N.getString("monitoring_configure");
			LOGGER.warn(message, e);
			writeMessage(req, resp, collectorController, message + '\n' + e.toString());
		} catch (final StreamCorruptedException e) {
			final String message = I18N.getFormattedString("reponse_non_comprise", appUrls);
			LOGGER.warn(message, e);
			writeMessage(req, resp, collectorController, message + '\n' + e.toString());
		} catch (final Exception e) {
			LOGGER.warn(e.toString(), e);
			writeMessage(req, resp, collectorController, e.toString());
		} finally {
			I18N.unbindLocale();
		}
	}

	private void writeMessage(HttpServletRequest req, HttpServletResponse resp,
			CollectorController collectorController, String message) throws IOException {
		collectorController.writeMessage(req, resp, collectorController.getApplication(req, resp),
				message);
	}

	/** {@inheritDoc} */
	@Override
	public void destroy() {
		LOGGER.info("collector servlet stopping");
		if (collectorServer != null) {
			collectorServer.stop();
		}
		Collector.stopJRobin();
		LOGGER.info("collector servlet stopped");
		super.destroy();
	}
}
