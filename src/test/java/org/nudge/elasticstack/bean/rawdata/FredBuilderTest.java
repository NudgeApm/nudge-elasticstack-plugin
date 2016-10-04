package org.nudge.elasticstack.bean.rawdata;

import com.nudge.apm.buffer.probe.RawDataProtocol;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

/**
 * Test class for {@link FredBuilder}
 */
public class FredBuilderTest {

	private static final Logger LOG = Logger.getLogger(FredBuilderTest.class);

	private RawDataProtocol.RawData rawData;

	/**
	 * Prepare the test by reading a sample example of a Nudge APM rawdata.
	 */
	@Before
	public void readRawdata() {
		try {
			rawData = RawDataProtocol.RawData.parseFrom(this.getClass().getClassLoader()
					.getResourceAsStream("rawdata/collecte_2016-09-29_10-54-01-620_140.dat"));
		} catch (IOException e) {
			LOG.error("Impossible to read the sample rawdata", e);
		}
	}

	@Test
	public void buildTransactions() throws Exception {
		List<TransactionFred> transactionFredList = FredBuilder.buildTransactions(rawData.getTransactionsList());

		// First test : transaction stuff
		RawDataProtocol.Transaction expectedTrans = rawData.getTransactionsList().get(0);
		TransactionFred transaction = transactionFredList.get(0);
		Assert.assertEquals(expectedTrans.getCode(), transaction.getCode());
		Assert.assertEquals(expectedTrans.getStartTime(), transaction.getStartTime());
		Assert.assertEquals(expectedTrans.getEndTime(), transaction.getEndTime());
		Assert.assertEquals(expectedTrans.getUserIp(), transaction.getUserIp());
		Assert.assertEquals(expectedTrans.getLayersList().size(), transaction.getLayers().size());

		// second test : layer stuff belongs to a transaction
		RawDataProtocol.Layer expectedLayer = expectedTrans.getLayersList().get(0);
		LayerFred layer = transaction.getLayers().get(0);
		Assert.assertEquals(expectedLayer.getLayerName(), layer.getLayerName());
		Assert.assertEquals(expectedLayer.getTime(), layer.getTime());
		Assert.assertEquals(expectedLayer.getCount(), layer.getCount());

		// third test : layer detail stuff belongs to a layer
		RawDataProtocol.LayerDetail expectedLayerDetail = expectedLayer.getCallsList().get(0);
		LayerFred.LayerDetail layerDetails = layer.getLayerDetails().get(0);
		Assert.assertEquals(expectedLayerDetail.getTimestamp(), layerDetails.getTimestamp());
		Assert.assertEquals(expectedLayerDetail.getCode(), layerDetails.getCode());
		Assert.assertEquals(expectedLayerDetail.getCount(), layerDetails.getCount());
		Assert.assertEquals(expectedLayerDetail.getTime(), layerDetails.getResponseTime());
	}

	@Test
	public void buildMBeans() throws Exception {
		List<MBeanFred> mbeanFredList = FredBuilder.buildMBeans(rawData.getMBeanList());

		RawDataProtocol.MBean expectedMbean = rawData.getMBeanList().get(0);
		MBeanFred mbean = mbeanFredList.get(0);
		Assert.assertEquals(expectedMbean.getAttributeInfoCount(), mbean.getAttributeInfoCount());
		Assert.assertEquals(expectedMbean.getCollectingTime(), mbean.getCollectingTime());
		Assert.assertEquals(expectedMbean.getObjectName(), mbean.getObjectName());

		RawDataProtocol.MBeanAttributeInfo expectedAttributeInfo = expectedMbean.getAttributeInfoList().get(0);
		MBeanFred.AttributeInfo attributeInfo = mbean.getAttributeInfos().get(0);
		Assert.assertEquals(expectedAttributeInfo.getNameId(), attributeInfo.getNameId());
		Assert.assertEquals(expectedAttributeInfo.getValue(), attributeInfo.getValue());
	}

}