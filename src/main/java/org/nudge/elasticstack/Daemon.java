package org.nudge.elasticstack;

/**
 * @author Sarah Bourgeois
 * @author Frederic Massart
 *
 * Description : Class which permits to send rawdatas to elasticSearch with -startDeamon
 */

import com.nudge.apm.buffer.probe.RawDataProtocol.Dictionary;
import com.nudge.apm.buffer.probe.RawDataProtocol.RawData;
import com.nudge.apm.buffer.probe.RawDataProtocol.Transaction;
import mapping.Mapping;
import mapping.Mapping.MappingType;
import org.apache.log4j.Logger;
import org.nudge.elasticstack.connection.Connection;
import org.nudge.elasticstack.context.elasticsearch.json.bean.EventMBean;
import org.nudge.elasticstack.context.elasticsearch.json.bean.EventSQL;
import org.nudge.elasticstack.context.elasticsearch.json.bean.EventTransaction;
import org.nudge.elasticstack.context.elasticsearch.json.bean.GeoLocation;
import org.nudge.elasticstack.context.elasticsearch.json.bean.GeoLocationWriter;
import org.nudge.elasticstack.context.elasticsearch.json.builder.GeoLocationElasticPusher;
import org.nudge.elasticstack.context.elasticsearch.json.builder.MBean;
import org.nudge.elasticstack.context.elasticsearch.json.builder.SQLLayer;
import org.nudge.elasticstack.context.elasticsearch.json.builder.TransactionLayer;
import org.nudge.elasticstack.context.nudge.dto.DTOBuilder;
import org.nudge.elasticstack.context.nudge.dto.MBeanDTO;
import org.nudge.elasticstack.context.nudge.dto.TransactionDTO;
import org.nudge.elasticstack.context.nudge.filter.bean.Filter;
import org.nudge.elasticstack.service.GeoLocationService;
import org.nudge.elasticstack.service.impl.GeoFreeGeoIpImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class Daemon {
	private static final Logger LOG = Logger.getLogger("Connector : ");
	private static ScheduledExecutorService scheduler;
	private static List<String> analyzedFilenames = new ArrayList<>();

	/**
	 * Description : Launcher Deamon.
	 * 
	 * @param config
	 * @throws NudgeESConnectorException
	 *
	 */
	public static void start(Configuration config) {
		scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
			public Thread newThread(Runnable runnable) {
				Thread thread = new Thread(runnable);
				thread.setName("nudge-es-daemon");
				thread.setDaemon(false);
				return thread;
			}
		});
		scheduleDaemon(scheduler, config);
	}

	private static void scheduleDaemon(ScheduledExecutorService scheduler, Configuration config) {
		scheduler.scheduleAtFixedRate(new DaemonTask(config), 0L, 1L, TimeUnit.MINUTES);
	}

	public static void stop() {
		scheduler.shutdown();
	}

	protected static class DaemonTask implements Runnable {
		private final Configuration config;
		private final GeoLocationService geoLocationService;
		// TODO Should be size limited => replace with a cache
		private final Map<String, GeoLocation> geoLocationsMap;

		DaemonTask(Configuration config) {
			this.config = config;
			geoLocationService = new GeoFreeGeoIpImpl();
			geoLocationsMap = new HashMap<>();

			// Mapping
			Mapping mapping = new Mapping();
			try {
				// Transaction update mapping
				mapping.pushMapping(config, MappingType.TRANSACTION);
				// Sql update mapping
				mapping.pushMapping(config, MappingType.SQL);
				// MBean update mapping
				mapping.pushMapping(config, MappingType.MBEAN);
				// GeoLocation mapping
				mapping.pushGeolocationMapping(config);
			} catch (IOException e) {
				throw new IllegalStateException("Failed to init mapping", e);
			}
		}

		/**
		 * Description : Call connector methods and run it
		 */
		@Override
		public void run() {
			try {
				// Connection and load configuration
				Connection c = new Connection(config.getNudgeUrl(), config.getNudgeApiToken());
				for (String appId : config.getAppIds()) {
					List<String> rawdataList = c.getRawdataList(appId, "-10m");
					// analyse files, comparaison and push
					for (String rawdataFilename : rawdataList) {
						if (!analyzedFilenames.contains(rawdataFilename)) {
							RawData rawdata = c.getRawdata(appId, rawdataFilename);

							// Request Filters
							List<Filter> filters = c.requestFilters(appId);

							// ==============================
							// Type : Transaction and Layer
							// ==============================
							List<Transaction> transactions = rawdata.getTransactionsList();
							List<TransactionDTO> transactionDTOs = DTOBuilder.buildTransactions(transactions, filters);

							TransactionLayer transactionLayer = new TransactionLayer();
							List<EventTransaction> events = transactionLayer.buildTransactionEvents(transactionDTOs);
							for (EventTransaction eventTrans : events) {
								transactionLayer.nullLayer(eventTrans);
							}
							List<String> jsonEvents = transactionLayer.parseJson(events);
							transactionLayer.sendToElastic(jsonEvents);

							// ===========================
							// Type : MBean
							// ===========================
							MBean mb = new MBean();
							List<com.nudge.apm.buffer.probe.RawDataProtocol.MBean> mbean = rawdata.getMBeanList();

							List<MBeanDTO> mBeans = DTOBuilder.buildMBeans(mbean);

							Dictionary dictionary = rawdata.getMbeanDictionary();
							List<EventMBean> eventsMBeans = mb.buildMbeanEvents(mBeans, dictionary);
							List<String> jsonEvents2 = mb.parseJsonMBean(eventsMBeans);
							mb.sendElk(jsonEvents2);

							// ===========================
							// Type : SQL
							// ===========================
							SQLLayer s = new SQLLayer();
							List<EventSQL> sql = s.buildSQLEvents(transactionDTOs);
							List<String> jsonEventsSql = s.parseJsonSQL(sql);
							s.sendSqltoElk(jsonEventsSql);
							
							// ===========================
							// GeoLocalation
							// ===========================
							List<GeoLocation> geoLocations = new ArrayList<>();
							GeoLocationElasticPusher gep = new GeoLocationElasticPusher();
							try {
								for (TransactionDTO transaction : transactionDTOs) {
									String userIp = transaction.getUserIp();
									if (userIp != null && !"".equals(userIp)) {
										GeoLocation geoLocation = geoLocationsMap.get(userIp);
										if(geoLocation == null) {
											LOG.debug("looking for "+userIp);
											geoLocation = geoLocationService.requestGeoLocationFromIp(userIp);
											geoLocationsMap.put(userIp, geoLocation);
										}
										geoLocations.add(geoLocation);
									}
								}
							} catch (NudgeESConnectorException e) {
								LOG.error("Failed to consider geolocation", e);
							}
							List<GeoLocationWriter> location = gep.buildLocationEvents(geoLocations, transactionDTOs);
							List<String> json = gep.parseJsonLocation(location);
							gep.sendElk(json);
						}
					}
					analyzedFilenames = rawdataList;
				}
			} catch (Throwable t) {
				LOG.error("Uncaptured error", t);
			}
		}
	}

}
