package at.rtr.rmbt.utils;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.util.Optional;

import lombok.Setter;

public abstract class GeoIpHelper
{
    private static final Logger log = LoggerFactory.getLogger(GeoIpHelper.class);

    private static volatile boolean lookupServiceFailure;
    private static volatile DatabaseReader lookupService;
    private final static Object LOOKUP_SERVICE_LOCK = new Object();

    @Setter
    private static String geoIpDbPath = "/var/lib/GeoIP/GeoLite2-Country.mmdb";

    private static Optional<DatabaseReader> getLookupService() {
        if (lookupService != null) {
            return Optional.of(lookupService);
        }
        synchronized (LOOKUP_SERVICE_LOCK) {
            if (lookupServiceFailure) {
                return Optional.empty();
            } else {
                // A File object pointing to your GeoIP2 or GeoLite2 database
                File database = new File(geoIpDbPath);
                try {
                    lookupService = new DatabaseReader.Builder(database).build();
                } catch (Exception e) {
                    lookupServiceFailure = true;
                    log.error("Maxmind GeoIP database could not be loaded", e);
                }

                return Optional.ofNullable(lookupService);
            }
        }
    }

    public static String lookupCountry(final InetAddress adr) {
        return getLookupService()
            .map(lookupService -> lookupCountryResponse(adr, lookupService))
            .map(CountryResponse::getCountry)
            .map(Country::getIsoCode)
            .orElse("");
    }

    private static CountryResponse lookupCountryResponse(final InetAddress addr, final DatabaseReader lookupService) {
        try {
            return lookupService.country(addr);
        } catch (Exception e) {
            log.error("Error while looking up country for ip address: {}", addr, e);
            return null;
        }
    }
}

