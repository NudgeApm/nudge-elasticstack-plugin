package org.nudge.elasticstack.bean.rawdata;

import java.util.List;

/**
 * Layer Bean of a {@link TransactionFred}.
 *
 * @author : Sarah Bourgeois
 * @author : Frederic Massart
 */
public class LayerFred {

    private String layerName;
    private long time;
    private long count;

    private List<LayerDetail> layerDetails;

    public String getLayerName() {
        return layerName;
    }

    public void setLayerName(String layerName) {
        this.layerName = layerName;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public List<LayerDetail> getLayerDetails() {
        return layerDetails;
    }

    public void setLayerDetails(List<LayerDetail> layerDetails) {
        this.layerDetails = layerDetails;
    }


    public class LayerDetail {

        private String code;
        private long count;
        private long responseTime;
        private long timestamp;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public long getResponseTime() {
            return responseTime;
        }

        public void setResponseTime(long responseTime) {
            this.responseTime = responseTime;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
