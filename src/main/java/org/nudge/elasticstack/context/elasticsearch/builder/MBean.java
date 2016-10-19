package org.nudge.elasticstack.context.elasticsearch.builder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nudge.apm.buffer.probe.RawDataProtocol.Dictionary;
import com.nudge.apm.buffer.probe.RawDataProtocol.Dictionary.DictionaryEntry;
import org.apache.log4j.Logger;
import org.nudge.elasticstack.BulkFormat;
import org.nudge.elasticstack.Configuration;
import org.nudge.elasticstack.context.elasticsearch.json.EventType;
import org.nudge.elasticstack.context.elasticsearch.json.bean.EventMBean;
import org.nudge.elasticstack.context.nudge.dto.MBeanDTO;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.nudge.elasticstack.Utils.ES_DATE_FORMAT;


public class MBean {

	private static final Logger LOG = Logger.getLogger(MBean.class.getName());
	private static final String lineBreak = "\n";
	Configuration config = new Configuration();

	/**
	 * Description : retrieve MBean from rawdata
	 *
	 * @param mbean
	 * @param dictionary
	 * @return
	 * @throws JsonProcessingException
	 */
	public List<EventMBean> buildMbeanEvents(List<MBeanDTO> mbean, Dictionary dictionary) throws JsonProcessingException {
		List<EventMBean> eventsMbean = new ArrayList<EventMBean>();
		List<DictionaryEntry> dico = dictionary.getDictionaryList();

		// retrieve MBean
		for (MBeanDTO mb : mbean) {

			String collectingTime = ES_DATE_FORMAT.format(mb.getCollectingTime());
			String objectName = mb.getObjectName();
			int countAttribute = mb.getAttributeInfoCount();

			for (MBeanDTO.AttributeInfo mBeanAttributeInfo : mb.getAttributeInfos()) {
				String nameMbean = null;
				double valueMbean = 0;
			int	nameId = mBeanAttributeInfo.getNameId();
				try {
					valueMbean = Double.parseDouble(mBeanAttributeInfo.getValue());
				} catch (NumberFormatException nfe) { 
					if (LOG.isDebugEnabled()) {
						LOG.debug("Impossible to get the value of a mbean, it will not be inserted to ELK. MBean from rawdata : " + mBeanAttributeInfo);
					}
				}
				EventMBean mbeanEvent = new EventMBean(nameMbean, objectName, EventType.MBEAN, valueMbean, collectingTime, countAttribute);
				// TODO FMA export the dicotionary stuff at the place that mbean dto are created
				// retrieve nameMbean with Dictionary
				for (DictionaryEntry dictionaryEntry : dico) {
					int id = dictionaryEntry.getId();
					if (nameId == id) {
						mbeanEvent.setNameMbean(dictionaryEntry.getName());
					}
				}
				// add events
				eventsMbean.add(mbeanEvent);
			}
		}
		return eventsMbean;
	}

	/**
	 * Description : Parse MBean to send to Elastic
	 *
	 * @param eventList
	 * @return
	 * @throws Exception
	 */
	public List<String> parseJsonMBean(List<EventMBean> eventList) throws Exception {
		List<String> jsonEvents2 = new ArrayList<String>();
		ObjectMapper jsonSerializer = new ObjectMapper();

		if (config.getDryRun()) {
			jsonSerializer.enable(SerializationFeature.INDENT_OUTPUT);
		}

		for (EventMBean event : eventList) {
			String jsonMetadata = generateMetaDataMbean(event.getType());
			jsonEvents2.add(jsonMetadata + lineBreak);
			// Handle data event
			String jsonEvent = jsonSerializer.writeValueAsString(event);
			jsonEvents2.add(jsonEvent + lineBreak);
        }
		LOG.debug(jsonEvents2);
		return jsonEvents2;
	}

	/**
	 * Description : generate MBean for Bulk api
	 *
	 * @param mbean
	 * @return
	 * @throws JsonProcessingException
	 */
	public String generateMetaDataMbean(EventType eventType) throws JsonProcessingException {
		Configuration conf = new Configuration();
		ObjectMapper jsonSerializer = new ObjectMapper();
		if (config.getDryRun()) {
			jsonSerializer.enable(SerializationFeature.INDENT_OUTPUT);
		}
		BulkFormat elasticMetaData = new BulkFormat();
		elasticMetaData.getIndexElement().setIndex(conf.getElasticIndex());
		elasticMetaData.getIndexElement().setType(eventType.toString());
		return jsonSerializer.writeValueAsString(elasticMetaData);
	}

	/**
	 * Description : Send MBean into elasticSearch
	 *
	 * @param jsonEvents2
	 * @throws IOException
	 */
	public void sendElk(List<String> jsonEvents2) throws IOException {
		Configuration conf = new Configuration();
		StringBuilder sb = new StringBuilder();

		for (String json : jsonEvents2) {
			sb.append(json);
		}
		if (config.getDryRun()) {
			LOG.info("Dry run active, only log documents, don't push to elasticsearch.");
			return;
		}
		long start = System.currentTimeMillis();
		URL URL = new URL(conf.getOutputElasticHosts() + "_bulk");
		if (LOG.isDebugEnabled()) {
			LOG.debug("Bulk request to : " + URL);
		}
		HttpURLConnection httpCon2 = (HttpURLConnection) URL.openConnection();
		httpCon2.setDoOutput(true);
		httpCon2.setRequestMethod("PUT");
		OutputStreamWriter out = new OutputStreamWriter(httpCon2.getOutputStream());
		out.write(sb.toString());
		out.close();
		long end = System.currentTimeMillis();
		long totalTime = end - start;
		LOG.info(" Flush " + jsonEvents2.size() + " documents insert in BULK in : " + (totalTime / 1000f) + "sec");
		LOG.debug(" Sending MBean : " + httpCon2.getResponseCode() + " - " + httpCon2.getResponseMessage());
	}

}