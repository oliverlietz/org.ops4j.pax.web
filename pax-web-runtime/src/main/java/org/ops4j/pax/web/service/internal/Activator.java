/* Copyright 2007 Niclas Hedhman.
 * Copyright 2007 Alin Dreghiciu.
 * Copyright 2011 Achim Nierbeck.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.web.service.internal;

import static org.ops4j.pax.web.jsp.JspWebdefaults.PROPERTY_JSP_CHECK_INTERVAL;
import static org.ops4j.pax.web.jsp.JspWebdefaults.PROPERTY_JSP_DEBUG_INFO;
import static org.ops4j.pax.web.jsp.JspWebdefaults.PROPERTY_JSP_DEVELOPMENT;
import static org.ops4j.pax.web.jsp.JspWebdefaults.PROPERTY_JSP_ENABLE_POOLING;
import static org.ops4j.pax.web.jsp.JspWebdefaults.PROPERTY_JSP_IE_CLASS_ID;
import static org.ops4j.pax.web.jsp.JspWebdefaults.PROPERTY_JSP_JAVA_ENCODING;
import static org.ops4j.pax.web.jsp.JspWebdefaults.PROPERTY_JSP_KEEP_GENERATED;
import static org.ops4j.pax.web.jsp.JspWebdefaults.PROPERTY_JSP_LOG_VERBOSITY_LEVEL;
import static org.ops4j.pax.web.jsp.JspWebdefaults.PROPERTY_JSP_MAPPED_FILE;
import static org.ops4j.pax.web.jsp.JspWebdefaults.PROPERTY_JSP_PRECOMPILATION;
import static org.ops4j.pax.web.jsp.JspWebdefaults.PROPERTY_JSP_SCRATCH_DIR;
import static org.ops4j.pax.web.jsp.JspWebdefaults.PROPERTY_JSP_TAGPOOL_MAX_SIZE;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_HTTP_CONNECTOR_NAME;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_HTTP_ENABLED;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_HTTP_PORT;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_HTTP_SECURE_CONNECTOR_NAME;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_HTTP_SECURE_ENABLED;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_HTTP_SECURE_PORT;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_HTTP_USE_NIO;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_LISTENING_ADDRESSES;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_LOG_NCSA_APPEND;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_LOG_NCSA_DISPATCH;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_LOG_NCSA_EXTENDED;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_LOG_NCSA_FORMAT;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_LOG_NCSA_LOGTIMEZONE;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_LOG_NCSA_RETAINDAYS;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SERVER_CONFIGURATION_FILE;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SERVER_CONFIGURATION_URL;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SESSION_COOKIE;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SESSION_DOMAIN;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SESSION_PATH;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SESSION_COOKIE_SECURE;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SESSION_TIMEOUT;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SESSION_URL;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SSL_CLIENT_AUTH_NEEDED;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SSL_CLIENT_AUTH_WANTED;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SSL_KEYPASSWORD;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SSL_KEYSTORE;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SSL_KEYSTORE_TYPE;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_SSL_PASSWORD;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_CIPHERSUITE_INCLUDED;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_CIPHERSUITE_EXCLUDED;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_TEMP_DIR;
import static org.ops4j.pax.web.service.WebContainerConstants.PROPERTY_WORKER_NAME;

import java.io.File;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.ops4j.pax.swissbox.property.BundleContextPropertyResolver;
import org.ops4j.pax.web.service.WebContainer;
import org.ops4j.pax.web.service.internal.util.SupportUtils;
import org.ops4j.pax.web.service.spi.Configuration;
import org.ops4j.pax.web.service.spi.ServerController;
import org.ops4j.pax.web.service.spi.ServerControllerFactory;
import org.ops4j.pax.web.service.spi.ServletListener;
import org.ops4j.pax.web.service.spi.model.ServerModel;
import org.ops4j.util.property.DictionaryPropertyResolver;
import org.ops4j.util.property.PropertyResolver;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.http.HttpService;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {

	private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

	private ServerController serverController;
	private ServiceRegistration<?> httpServiceFactoryReg;

	private BundleContext bundleContext;

	private ServletEventDispatcher servletEventDispatcher;

	private ServiceTracker<EventAdmin, EventAdmin> eventServiceTracker;

	private ServiceTracker<LogService, LogService> logServiceTracker;

	private ServiceTracker<ServerControllerFactory, ServerControllerFactory> dynamicsServiceTracker;

	private final ExecutorService configExecutor = new ThreadPoolExecutor(0, 1,
			20, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

	private Dictionary<String, ?> config;

	private ServerControllerFactory factory;

	private boolean initialConfigSet;

	public Activator() {
	}

	@Override
	public void start(final BundleContext context) throws Exception {
		LOG.debug("Starting Pax Web");
		this.bundleContext = context;
		servletEventDispatcher = new ServletEventDispatcher(context);
		if (SupportUtils.isEventAdminAvailable()) {
			// Do use the filters this way the eventadmin packages can be
			// resolved optional!
			Filter filterEvent = context
					.createFilter("(objectClass=org.osgi.service.event.EventAdmin)");
			EventAdminHandler adminHandler = new EventAdminHandler(
					context);
			eventServiceTracker = new ServiceTracker<EventAdmin, EventAdmin>(
					context, filterEvent, adminHandler);
			eventServiceTracker.open();
			context.registerService(ServletListener.class, adminHandler,
					null);
			LOG.info("EventAdmin support enabled, servlet events will be postet to topics.");
		} else {
			LOG.info("EventAdmin support is not available, no servlet events will be posted!");
		}
		if (SupportUtils.isLogServiceAvailable()) {
			// Do use the filters this way the logservice packages can be
			// resolved optional!
			Filter filterLog = context
					.createFilter("(objectClass=org.osgi.service.log.LogService)");
			LogServiceHandler logServiceHandler = new LogServiceHandler(
					context);
			logServiceTracker = new ServiceTracker<LogService, LogService>(
					context, filterLog, logServiceHandler);
			logServiceTracker.open();
			context.registerService(ServletListener.class,
					logServiceHandler, null);
			LOG.info("LogService support enabled, log events will be created.");
		} else {
			LOG.info("LogService support is not available, no log events will be created!");
		}

		if (SupportUtils.isManagedServiceAvailable()) {
			createManagedService(context);
		} else {
			scheduleUpdateConfig(null);
		}

		// special handling for JSP Compiler
		if (SupportUtils.isJSPAvailable()) {
			System.setProperty("org.apache.jasper.compiler.disablejsr199",
					Boolean.TRUE.toString());
		}

		LOG.info("Pax Web started");
	}

	@Override
	public void stop(final BundleContext context) {
		LOG.debug("Stopping Pax Web...");

		if (dynamicsServiceTracker != null) {
			dynamicsServiceTracker.close();
		}
		if (logServiceTracker != null) {
			logServiceTracker.close();
		}
		if (eventServiceTracker != null) {
			eventServiceTracker.close();
		}
		if (servletEventDispatcher != null) {
			servletEventDispatcher.destroy();
		}
		// Wait up to 20 seconds, otherwhise
		try {
			configExecutor.shutdown();
			LOG.debug("...entering 20 seconds grace period...");
			configExecutor.awaitTermination(20, TimeUnit.SECONDS);
			configExecutor.shutdownNow();
		} catch (InterruptedException e) {
			// Ignore, we are done anyways...
		}
		LOG.info("Pax Web stopped");
	}

	/**
	 * Registers a managed service to listen on configuration updates.
	 * 
	 * @param context
	 *            bundle context to use for registration
	 */
	private void createManagedService(final BundleContext context) {
		ManagedService service = new ManagedService() {
			@Override
			public void updated(final Dictionary<String, ?> configuration)
					throws ConfigurationException {
				scheduleUpdateConfig(configuration);
			}
		};
		final Dictionary<String, String> props = new Hashtable<String, String>();
		props.put(Constants.SERVICE_PID,
				org.ops4j.pax.web.service.WebContainerConstants.PID);
		context.registerService(ManagedService.class, service, props);
		// If ConfigurationAdmin service is not available, then do a default
		// configuration.
		// In other cases, ConfigurationAdmin service will always call the
		// ManagedService.
		if (context.getServiceReference(ConfigurationAdmin.class
				.getName()) == null) {
			try {
				service.updated(null);
			} catch (ConfigurationException ignore) {
				// this should never happen
				LOG.error(
						"Internal error. Cannot set initial configuration resolver.",
						ignore);
			}
		}
	}

	protected boolean same(Dictionary<String, ?> cfg1,
			Dictionary<String, ?> cfg2) {
		if (cfg1 == null) {
			return cfg2 == null;
		} else if (cfg2 == null) {
			return false;
		} else if (cfg1.size() != cfg2.size()) {
			return false;
		} else {
			boolean result = true;
			Enumeration<String> keys = cfg1.keys();
			while (result && keys.hasMoreElements()) {
				String key = keys.nextElement();
				Object v1 = cfg1.get(key);
				Object v2 = cfg2.get(key);
				result = same(v1, v2);
			}
			return result;
		}
	}

	protected boolean same(Object v1, Object v2) {
		if (v1 == null) {
			return v2 == null;
		} else if (v2 == null) {
			return false;
		} else {
			return v1 == v2 || v1.equals(v2);
		}
	}

	private void scheduleUpdateConfig(final Dictionary<String, ?> configuration) {
		configExecutor.submit(new Runnable() {
			@Override
			public void run() {
				updateController(configuration, factory);
			}
		});
	}

	private void scheduleUpdateFactory(final ServerControllerFactory controllerFactory) {
		Future<?> future = configExecutor.submit(new Runnable() {
			@Override
			public void run() {
				updateController(config, controllerFactory);
			}
		});
        // Make sure we destroy things synchronously
        if (controllerFactory == null) {
            try {
                future.get(20, TimeUnit.SECONDS);
                // CHECKSTYLE:OFF
            } catch (Exception e) {
                LOG.info("Error when updating factory: " + e.getMessage(), e);
            }
            // CHECKSTYLE:ON
        }
	}

	/**
	 * This method is the only place which is allowed to modify the config and
	 * factory fields.
	 * 
	 * @param dictionary
	 * @param controllerFactory
	 */
	protected void updateController(Dictionary<String, ?> dictionary,
			ServerControllerFactory controllerFactory) {
		// We want to make sure the configuration is known before starting the
		// service tracker, else the configuration could be set after the
		// service is found which would cause a restart of the service
		if (!initialConfigSet) {
			initialConfigSet = true;
			this.config = dictionary;
			this.factory = controllerFactory;
			dynamicsServiceTracker = new ServiceTracker<ServerControllerFactory, ServerControllerFactory>(
					bundleContext, ServerControllerFactory.class,
					new DynamicsServiceTrackerCustomizer());
			dynamicsServiceTracker.open();
			return;
		}
		if (same(dictionary, this.config) && same(controllerFactory, this.factory)) {
			return;
		}
		if (httpServiceFactoryReg != null) {
			httpServiceFactoryReg.unregister();
			httpServiceFactoryReg = null;
		}
		if (serverController != null) {
			serverController.stop();
			serverController = null;
		}
		if (controllerFactory != null) {
			try {
				final PropertyResolver tmpResolver = new BundleContextPropertyResolver(
						bundleContext, new DefaultPropertyResolver());
				final PropertyResolver resolver = dictionary != null ? new DictionaryPropertyResolver(
						dictionary, tmpResolver) : tmpResolver;
				final ConfigurationImpl configuration = new ConfigurationImpl(
						resolver);
				final ServerModel serverModel = new ServerModel();
				serverController = controllerFactory.createServerController(serverModel);
				serverController.configure(configuration);
				Dictionary<String, Object> props = determineServiceProperties(
						dictionary, configuration,
						serverController.getHttpPort(),
						serverController.getHttpSecurePort());
				httpServiceFactoryReg = bundleContext.registerService(
						new String[] { HttpService.class.getName(),
								WebContainer.class.getName() },
						new HttpServiceFactoryImpl() {
							@Override
							HttpService createService(final Bundle bundle) {
								return new HttpServiceProxy(new HttpServiceStarted(
													bundle, serverController, serverModel,
													servletEventDispatcher));
							}
						}, props);
				if (!serverController.isStarted()) {
					while (!serverController.isConfigured()) {
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							LOG.warn(
									"caught interruptexception while waiting for configuration",
									e);
							Thread.currentThread().interrupt();
							return;
						}
					}
					serverController.start();
				}
				//CHECKSTYLE:OFF
			} catch (Throwable t) {
                // TODO: ignore those exceptions if the bundle is being stopped
				LOG.error("Unable to start pax web server: " + t.getMessage(),
						t);
			}
			//CHECKSTYLE:ON
		}
		this.factory = controllerFactory;
		this.config = dictionary;
	}

	private Dictionary<String, Object> determineServiceProperties(
			final Dictionary<String, ?> managedConfig,
			final Configuration configuration, final Integer httpPort,
			final Integer httpSecurePort) {
		
		final Hashtable<String, Object> toPropagate = new Hashtable<String, Object>();
		// first store all configuration properties as received via managed
		// service
		if (managedConfig != null && !managedConfig.isEmpty()) {
			final Enumeration<String> enumeration = managedConfig.keys();
			while (enumeration.hasMoreElements()) {
				String key = enumeration.nextElement();
				toPropagate.put(key, managedConfig.get(key));
			}
		}

		// then add/replace configuration properties
		setProperty(toPropagate, PROPERTY_HTTP_ENABLED, configuration.isHttpEnabled());
		setProperty(toPropagate, PROPERTY_HTTP_PORT, configuration.getHttpPort());
		setProperty(toPropagate, PROPERTY_HTTP_CONNECTOR_NAME,
				configuration.getHttpConnectorName());
		setProperty(toPropagate, PROPERTY_HTTP_SECURE_ENABLED,
				configuration.isHttpSecureEnabled());
		setProperty(toPropagate, PROPERTY_HTTP_SECURE_PORT,
				configuration.getHttpSecurePort());
		setProperty(toPropagate, PROPERTY_HTTP_SECURE_CONNECTOR_NAME,
				configuration.getHttpSecureConnectorName());
		setProperty(toPropagate, PROPERTY_HTTP_USE_NIO, configuration.useNIO());
		setProperty(toPropagate, PROPERTY_SSL_CLIENT_AUTH_NEEDED,
				configuration.isClientAuthNeeded());
		setProperty(toPropagate, PROPERTY_SSL_CLIENT_AUTH_WANTED,
				configuration.isClientAuthWanted());
		setProperty(toPropagate, PROPERTY_SSL_KEYSTORE, configuration.getSslKeystore());
		setProperty(toPropagate, PROPERTY_SSL_KEYSTORE_TYPE,
				configuration.getSslKeystoreType());
		setProperty(toPropagate, PROPERTY_SSL_PASSWORD, configuration.getSslPassword());
		setProperty(toPropagate, PROPERTY_SSL_KEYPASSWORD,
				configuration.getSslKeyPassword());
		setProperty(toPropagate, PROPERTY_CIPHERSUITE_INCLUDED, configuration.getCiphersuiteIncluded());
		setProperty(toPropagate, PROPERTY_CIPHERSUITE_EXCLUDED, configuration.getCiphersuiteExcluded());
		setProperty(toPropagate, PROPERTY_TEMP_DIR,
				configuration.getTemporaryDirectory());
		setProperty(toPropagate, PROPERTY_SESSION_TIMEOUT,
				configuration.getSessionTimeout());
		setProperty(toPropagate, PROPERTY_SESSION_URL, configuration.getSessionUrl());
		setProperty(toPropagate, PROPERTY_SESSION_COOKIE,
				configuration.getSessionCookie());
		setProperty(toPropagate, PROPERTY_SESSION_DOMAIN,
				configuration.getSessionDomain());
		setProperty(toPropagate, PROPERTY_SESSION_PATH,
				configuration.getSessionPath());
		setProperty(toPropagate, PROPERTY_SESSION_COOKIE_SECURE,
				configuration.getSessionCookieSecure());
		setProperty(toPropagate, PROPERTY_WORKER_NAME, configuration.getWorkerName());
		setProperty(toPropagate, PROPERTY_LISTENING_ADDRESSES,
				configuration.getListeningAddresses());

		// then replace ports
		setProperty(toPropagate, PROPERTY_HTTP_PORT, httpPort);
		setProperty(toPropagate, PROPERTY_HTTP_SECURE_PORT, httpSecurePort);

		// then add/replace configuration properties for external jetty.xml file
		setProperty(toPropagate, PROPERTY_SERVER_CONFIGURATION_FILE,
				configuration.getConfigurationDir());

		setProperty(toPropagate, PROPERTY_SERVER_CONFIGURATION_URL,
				configuration.getConfigurationURL());

		// Request Log - e.g NCSA log
		setProperty(toPropagate, PROPERTY_LOG_NCSA_FORMAT,
				configuration.getLogNCSAFormat());
		setProperty(toPropagate, PROPERTY_LOG_NCSA_RETAINDAYS,
				configuration.getLogNCSARetainDays());
		setProperty(toPropagate, PROPERTY_LOG_NCSA_APPEND,
				configuration.isLogNCSAAppend());
		setProperty(toPropagate, PROPERTY_LOG_NCSA_EXTENDED,
				configuration.isLogNCSAExtended());
		setProperty(toPropagate, PROPERTY_LOG_NCSA_DISPATCH,
				configuration.isLogNCSADispatch());
		setProperty(toPropagate, PROPERTY_LOG_NCSA_DISPATCH,
				configuration.isLogNCSADispatch());
		setProperty(toPropagate, PROPERTY_LOG_NCSA_LOGTIMEZONE,
				configuration.getLogNCSATimeZone());

		if (SupportUtils.isJSPAvailable()) {
			setProperty(toPropagate, PROPERTY_JSP_CHECK_INTERVAL,
					configuration.getJspCheckInterval());
			setProperty(toPropagate, PROPERTY_JSP_DEBUG_INFO,
					configuration.getJspClassDebugInfo());
			setProperty(toPropagate, PROPERTY_JSP_DEVELOPMENT,
					configuration.getJspDevelopment());
			setProperty(toPropagate, PROPERTY_JSP_ENABLE_POOLING,
					configuration.getJspEnablePooling());
			setProperty(toPropagate, PROPERTY_JSP_IE_CLASS_ID,
					configuration.getJspIeClassId());
			setProperty(toPropagate, PROPERTY_JSP_JAVA_ENCODING,
					configuration.getJspJavaEncoding());
			setProperty(toPropagate, PROPERTY_JSP_KEEP_GENERATED,
					configuration.getJspKeepgenerated());
			setProperty(toPropagate, PROPERTY_JSP_LOG_VERBOSITY_LEVEL,
					configuration.getJspLogVerbosityLevel());
			setProperty(toPropagate, PROPERTY_JSP_MAPPED_FILE,
					configuration.getJspMappedfile());
			setProperty(toPropagate, PROPERTY_JSP_SCRATCH_DIR,
					configuration.getJspScratchDir());
			setProperty(toPropagate, PROPERTY_JSP_TAGPOOL_MAX_SIZE,
					configuration.getJspTagpoolMaxSize());
			setProperty(toPropagate, PROPERTY_JSP_PRECOMPILATION,
					configuration.getJspPrecompilation());
		}

		return toPropagate;
	}

	private void setProperty(final Hashtable<String, Object> properties,
			final String name, final Object value) {
		if (value != null) {
			if (value instanceof File) {
				properties.put(name, ((File) value).getAbsolutePath());
			} else if (value instanceof Object[]) {
				properties.put(name, join(",", (Object[]) value));
			} else {
				properties.put(name, value.toString());
			}
		} else {
			properties.remove(name);
		}
	}

	private static String join(String token, Object[] array) {
		if (array == null) {
			return null;
		}
		if (array.length == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder();

		for (int x = 0; x < (array.length - 1); x++) {
			if (array[x] != null) {
				sb.append(array[x].toString());
			} else {
				sb.append("null");
			}
			sb.append(token);
		}
		sb.append(array[array.length - 1]);

		return (sb.toString());
	}

	private class DynamicsServiceTrackerCustomizer
			implements
			ServiceTrackerCustomizer<ServerControllerFactory, ServerControllerFactory> {

		@Override
		public ServerControllerFactory addingService(
				ServiceReference<ServerControllerFactory> reference) {
			final ServerControllerFactory controllerFactory = bundleContext
					.getService(reference);
			scheduleUpdateFactory(controllerFactory);
			return controllerFactory;
		}

		@Override
		public void modifiedService(
				ServiceReference<ServerControllerFactory> reference,
				ServerControllerFactory service) {
		}

		@Override
		public void removedService(
				ServiceReference<ServerControllerFactory> reference,
				ServerControllerFactory service) {
			if (bundleContext != null) {
				bundleContext.ungetService(reference);
			}
			scheduleUpdateFactory(null);
		}
	}

}
