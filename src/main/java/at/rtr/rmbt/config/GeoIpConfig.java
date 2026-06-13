package at.rtr.rmbt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import at.rtr.rmbt.utils.GeoIpHelper;
import jakarta.annotation.PostConstruct;

@Component
public class GeoIpConfig {

    @Value( "${app.geoIp.countryDbPath}")
    private String geoIpCountryDbPath;

    @Value( "${app.geoIp.asnDbPath}")
    private String geoIpAsnDbPath;

    @PostConstruct
    public void init() {
        if(StringUtils.hasText(geoIpCountryDbPath)) {
            GeoIpHelper.setGeoIpCountryDbPath(geoIpCountryDbPath);
        }
        if(StringUtils.hasText(geoIpAsnDbPath)) {
            GeoIpHelper.setGeoIpAsnDbPath(geoIpAsnDbPath);
        }
    }
}
