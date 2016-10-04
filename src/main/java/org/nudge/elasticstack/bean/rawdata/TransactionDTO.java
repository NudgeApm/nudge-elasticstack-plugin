package org.nudge.elasticstack.bean.rawdata;

import java.util.List;

/**
 * Transaction entity coming from Nudge APM data.
 *
 * @author : Sarah Bourgeois
 * @author : Frederic Massart
 */
public class TransactionDTO {

    private String code;
    private long startTime;
    private long endTime;

    private String userIp;

    private List<LayerDTO> layers;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getUserIp() {
        return userIp;
    }

    public void setUserIp(String userIp) {
        this.userIp = userIp;
    }

    public List<LayerDTO> getLayers() {
        return layers;
    }

    public void setLayers(List<LayerDTO> layers) {
        this.layers = layers;
    }
    
}