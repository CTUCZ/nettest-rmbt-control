package at.rtr.rmbt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import at.rtr.rmbt.utils.GeoIpHelper;
import jakarta.annotation.PostConstruct;

@Component
public class GeoIpConfig {

    @Value( "${app.geoIp.dbPath}")
    private String geoIpDbPath;

    @PostConstruct
    public void init() {
        if(StringUtils.hasText(geoIpDbPath)) {
            GeoIpHelper.setGeoIpDbPath(geoIpDbPath);
        }
    }
}
