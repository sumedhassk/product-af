package org.wso2.carbon.logging.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.wso2.carbon.logging.service.data.LoggingConfig;
import org.wso2.carbon.logging.util.LoggingConstants;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecretResolverFactory;

public class LoggingConfigManager {

	private static final Log log = LogFactory.getLog(LoggingConfigManager.class);
	private static LoggingConfigManager cassandraConfig;
	private static BundleContext bundleContext;
    private static SecretResolver secretResolver;

	public static LoggingConfigManager getCassandraConfig() {
		return cassandraConfig;
	}

	public static void setBundleContext(BundleContext bundleContext) {
		LoggingConfigManager.bundleContext = bundleContext;
	}

	public static void setCassandraConfig(LoggingConfigManager syslogConfig) {
		LoggingConfigManager.cassandraConfig = syslogConfig;
	}

	public static Log getLog() {
		return log;
	}

	public LoggingConfig getSyslogData() {
		return null;
	}

	/**
	 * Returns the configurations from the Cassandra configuration file.
	 * 
	 * @return cassandra configurations
	 */
	public static LoggingConfig loadLoggingConfiguration() {
		// gets the configuration file name from the cassandra-config.xml.
		String cassandraConfigFileName = CarbonUtils.getCarbonConfigDirPath()
				+ RegistryConstants.PATH_SEPARATOR
				+ LoggingConstants.ETC_DIR
				+ RegistryConstants.PATH_SEPARATOR
				+ LoggingConstants.LOGGING_CONF_FILE;
		return loadLoggingConfiguration(cassandraConfigFileName);
	}

	private InputStream getInputStream(String configFilename)
			throws IOException {
		InputStream inStream = null;
		File configFile = new File(configFilename);
		if (configFile.exists()) {
			inStream = new FileInputStream(configFile);
		}
		String warningMessage = "";
		if (inStream == null) {
			URL url;
			if (bundleContext != null) {
				if ((url = bundleContext.getBundle().getResource(
						LoggingConstants.LOGGING_CONF_FILE)) != null) {
					inStream = url.openStream();
				} else {
					warningMessage = "Bundle context could not find resource "
							+ LoggingConstants.LOGGING_CONF_FILE
							+ " or user does not have sufficient permission to access the resource.";
					log.warn(warningMessage);
				}

			} else {
				if ((url = this.getClass().getClassLoader()
						.getResource(LoggingConstants.LOGGING_CONF_FILE)) != null) {
					inStream = url.openStream();
				} else {
					warningMessage = "Could not find resource "
							+ LoggingConstants.LOGGING_CONF_FILE
							+ " or user does not have sufficient permission to access the resource.";
					log.warn(warningMessage);
				}
			}
		}
		return inStream;
	}


	private static LoggingConfig loadDefaultConfiguration() {
		LoggingConfig config = new LoggingConfig();
		config.setCassandraServerAvailable(false);
		return config;
	}

	/**
	 * Loads the given Syslog Configuration file.
	 * 
	 * @param configFilename
	 *            Name of the configuration file
	 * @return the syslog configuration data.
	 */
	private static LoggingConfig loadLoggingConfiguration(
			String configFilename) {
		LoggingConfig config = new LoggingConfig();
		InputStream inputStream = null;
        String secretAlias = "";
		try {
			inputStream = new LoggingConfigManager()
					.getInputStream(configFilename);
		} catch (IOException e1) {
			log.error("Could not close the Configuration File "
					+ configFilename);
		}
		if (inputStream != null) {
			try {
				XMLStreamReader parser = XMLInputFactory.newInstance()
						.createXMLStreamReader(inputStream);
				StAXOMBuilder builder = new StAXOMBuilder(parser);
				OMElement documentElement = builder.getDocumentElement();
                secretResolver = SecretResolverFactory.create(documentElement, true);
				@SuppressWarnings("rawtypes")
				Iterator it = documentElement.getChildElements();
				while (it.hasNext()) {
					OMElement element = (OMElement) it.next();
					// Checks whether syslog configuration enable.
					if (LoggingConstants.CassandraConfigProperties.IS_CASSANDRA_AVAILABLE
							.equals(element.getLocalName())) {
						String isCassandraOn = element.getText();
						// by default, make the syslog off.
						boolean isCassandraAvailable = false;
						if (isCassandraOn.trim().equalsIgnoreCase("true")) {
							isCassandraAvailable = true;
						}
						config.setCassandraServerAvailable(isCassandraAvailable);
					} else if (LoggingConstants.CassandraConfigProperties.URL
							.equals(element.getLocalName())) {
						config.setUrl(element.getText());
					} else if (LoggingConstants.CassandraConfigProperties.USER_NAME
							.equals(element.getLocalName())) {
						config.setUser(element.getText());
					} else if (LoggingConstants.CassandraConfigProperties.PASSWORD
							.equals(element.getLocalName())) {
                        secretAlias = LoggingConstants.CassandraConfigProperties.SECRET_ALIAS_PASSWORD;
                        if (secretResolver != null && secretResolver.isInitialized()
                                && secretResolver.isTokenProtected(secretAlias)) {
                            config.setPassword(secretResolver.resolve(secretAlias));
                        } else {
                            config.setPassword(element.getText());
                        }
					} else if (LoggingConstants.CassandraConfigProperties.ARCHIVED_HOST
							.equals(element.getLocalName())) {
						config.setArchivedHost(element.getText());
                    } else if (LoggingConstants.CassandraConfigProperties.CONSISTENCY_LEVEL
                            .equals(element.getLocalName())) {
                        config.setConsistencyLevel(element.getText());
                    } else if (LoggingConstants.CassandraConfigProperties.AUTO_DISCOVERY
                            .equals(element.getLocalName())) {

                        String autoDiscoveryEnable = element.getAttributeValue(
                                new QName(LoggingConstants.CassandraConfigProperties.AUTO_DISCOVERY_ENABLE));
                        boolean isAutoDiscoveryEnable = false;
                        if (autoDiscoveryEnable.trim().equalsIgnoreCase("true")) {
                            isAutoDiscoveryEnable = true;
                        }
                        config.setAutoDiscoveryEnable(isAutoDiscoveryEnable);

                        String delay = element.getAttributeValue(
                                new QName(LoggingConstants.CassandraConfigProperties.AUTO_DISCOVERY_DELAY));
                        if (delay != null && !"".equals(delay.trim())) {
                            int delayAsInt = -1;
                            try {
                                delayAsInt = Integer.parseInt(delay.trim());
                            } catch (NumberFormatException ignored) {
                            }
                            if (delayAsInt > 0) {
                                config.setAutoDiscoveryDelay(delayAsInt);
                            }
                        }
                    } else if (LoggingConstants.CassandraConfigProperties.RETRY_DOWNED_HOSTS
                            .equals(element.getLocalName())) {

                        String retryDownedEnable = element.getAttributeValue(
                                new QName(LoggingConstants.CassandraConfigProperties.RETRY_DOWNED_HOSTS_ENABLE));
                        boolean isRetryDownedHostsEnable = false;
                        if (retryDownedEnable.trim().equalsIgnoreCase("true")) {
                            isRetryDownedHostsEnable = true;
                        }
                        config.setRetryDownedHostsEnable(isRetryDownedHostsEnable);

                        String queue = element.getAttributeValue(
                                new QName(LoggingConstants.CassandraConfigProperties.RETRY_DOWNED_HOSTS_QUEUE));
                        if (queue != null && !"".equals(queue.trim())) {
                            int queueAsInt = -1;
                            try {
                                queueAsInt = Integer.parseInt(queue.trim());
                            } catch (NumberFormatException ignored) {
                            }
                            if (queueAsInt > 0) {
                                config.setRetryDownedHostsQueueSize(queueAsInt);
                            }
                        }
                    } else if (LoggingConstants.CassandraConfigProperties.ARCHIVED_USER
							.equals(element.getLocalName())) {
						config.setArchivedUser(element.getText());
					} else if (LoggingConstants.CassandraConfigProperties.ARCHIVED_PASSWORD
							.equals(element.getLocalName())) {
                        secretAlias = LoggingConstants.CassandraConfigProperties.SECRET_ALIAS_ARCHIVED_PASSWORD;
                        if (secretResolver != null && secretResolver.isInitialized()
                                && secretResolver.isTokenProtected(secretAlias)) {
                            config.setArchivedPassword(secretResolver.resolve(secretAlias));
                        } else {
                            config.setArchivedPassword(element.getText());
                        }
					} else if (LoggingConstants.CassandraConfigProperties.ARCHIVED_PORT
							.equals(element.getLocalName())) {
						config.setArchivedPort(element.getText());
					} else if (LoggingConstants.CassandraConfigProperties.ARCHIVED_REALM
							.equals(element.getLocalName())) {
						config.setArchivedRealm(element.getText());
					} else if (LoggingConstants.CassandraConfigProperties.ARCHIVED_HDFS_PATH
							.equals(element.getLocalName())) {
						config.setArchivedHDFSPath(element.getText());
					}
				}
                //setting the default valus to config
                config.setColFamily("log");
                config.setCluster("admin");
                config.setKeyspace("EVENT_KS");
				return config;
			} catch (Exception e) {
				String msg = "Error in loading Stratos Configurations File: "
						+ configFilename + ". Default Settings will be used.";
				log.error(msg, e);
				return loadDefaultConfiguration(); // returns the default
													// configurations, if the
													// file could not be loaded.
			} finally {
				if (inputStream != null) {
					try {
						inputStream.close();
					} catch (IOException e) {
						log.error("Could not close the Configuration File "
								+ configFilename);
					}
				}
			}
		}
		log.error("Unable to locate the stratos configurations file. "
				+ "Default Settings will be used.");
		return loadDefaultConfiguration(); // return the default configurations,
											// if the file not found.
	}
}
