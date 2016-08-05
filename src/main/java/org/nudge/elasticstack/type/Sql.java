package org.nudge.elasticstack.type;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nudge.apm.buffer.probe.RawDataProtocol.Layer;
import com.nudge.apm.buffer.probe.RawDataProtocol.LayerDetail;
import com.nudge.apm.buffer.probe.RawDataProtocol.Transaction;
import org.apache.log4j.Logger;
import org.nudge.elasticstack.BulkFormat;
import org.nudge.elasticstack.config.Configuration;
import org.nudge.elasticstack.json.bean.EventSQL;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Sql {

	private static final Logger LOG = Logger.getLogger("Sql org.nudge.elasticstack.type :");
	private static final String lineBreak = "\n";
	Configuration config = new Configuration();

	/**
	 * Description : retrieve SQL request
	 */
	public List<EventSQL> buildSqlEvents(List<Transaction> transaction) {
		List<EventSQL> eventSqls = new ArrayList<>();
		List<Layer> layer = new ArrayList<>();
		List<LayerDetail> layerDetail = new ArrayList<>();
		for (Transaction trans : transaction) {
			trans.getLayersList();
			layer.addAll(trans.getLayersList());
		}
		for (Layer lay : layer) {
			lay.getCallsList();
			layerDetail.addAll(lay.getCallsList());
		}
		for (LayerDetail layd : layerDetail) {
			String sqlCode = null, sqlTimestamp;
			long sqlCount = 0, sqlTime = 0;
			sqlCode = layd.getCode();
			sqlCount = layd.getCount();
			sqlTime = layd.getTime();
			SimpleDateFormat sdfr = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
			sqlTimestamp = sdfr.format(layd.getTimestamp());
			EventSQL sqlevent = new EventSQL(sqlTimestamp, sqlCode, sqlCount, sqlTime);
			eventSqls.add(sqlevent);
		}
		return eventSqls;
	}

	/**
	 * Description : Parse SQL to send to Elastic
	 *
	 * @param eventList
	 * @return
	 * @throws Exception
	 */
	public List<String> parseJsonSQL(List<EventSQL> eventSqls) throws Exception {
		List<String> jsonEventsSql = new ArrayList<String>();
		ObjectMapper jsonSerializer = new ObjectMapper();

		if (config.getDryRun()) {
			jsonSerializer.enable(SerializationFeature.INDENT_OUTPUT);
		}
		for (EventSQL eventSql : eventSqls) {
			String jsonMetadata = generateMetaDataSQL(eventSql.getName());
			jsonEventsSql.add(jsonMetadata + lineBreak);
			// Handle data eventSql
			String jsonEvent = jsonSerializer.writeValueAsString(eventSql);
			jsonEventsSql.add(jsonEvent + lineBreak);
		}
		LOG.debug(jsonEventsSql);
		System.out.println(jsonEventsSql);
		return jsonEventsSql;
	}

	/**
	 * Description : generate SQL for Bulk api
	 *
	 * @param mbean
	 * @return
	 * @throws JsonProcessingException
	 */
	public String generateMetaDataSQL(String sql) throws JsonProcessingException {
		Configuration conf = new Configuration();
		ObjectMapper jsonSerializer = new ObjectMapper();
		if (config.getDryRun()) {
			jsonSerializer.enable(SerializationFeature.INDENT_OUTPUT);
		}
		BulkFormat elasticMetaData = new BulkFormat();
		elasticMetaData.getIndexElement().setIndex(conf.getElasticIndex());
		elasticMetaData.getIndexElement().setId(UUID.randomUUID().toString());
		elasticMetaData.getIndexElement().setType("sql");
		return jsonSerializer.writeValueAsString(elasticMetaData);
	}

	/**
	 * Description : Send MBean into elasticSearch
	 *
	 * @param jsonEvents2
	 * @throws IOException
	 */
	public void sendSqltoElk(List<String> jsonEventsSql) throws IOException {
		Configuration conf = new Configuration();
		StringBuilder sb = new StringBuilder();
		for (String json : jsonEventsSql) {
			sb.append(json);
		}
		if (config.getDryRun()) {
			LOG.debug("Dry run active, only log documents, don't push to elasticsearch.");
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
		LOG.info(" Flush " + jsonEventsSql.size() + " documents insert in BULK in : " + (totalTime / 1000f) + "sec");
		LOG.debug(" Sending Sql : " + httpCon2.getResponseCode() + " - " + httpCon2.getResponseMessage());
	}

} // End of class