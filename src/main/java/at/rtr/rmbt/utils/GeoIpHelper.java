package at.rtr.rmbt.utils;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CountryResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;

import lombok.Setter;

public abstract class GeoIpHelper
{
    private static final Logger log = LoggerFactory.getLogger(GeoIpHelper.class);

    private static volatile DatabaseReader countryLookupService;
    private static volatile DatabaseReader asnLookupService;

    // Retry control (separate for each DB) ...
    private static volatile long countryNextRetryAtMs;
    private static volatile long asnNextRetryAtMs;

    // ... and retry every 15 minutes after a failure
    private static final long MS_PER_MINUTE = 60 * 1000L;
    private static final long RETRY_DELAY_MS = 15 * MS_PER_MINUTE;

    private static final Object LOOKUP_SERVICE_LOCK = new Object();

    // CTU: path of the COUNTRY database is configurable via app.geoIp.dbPath (injected by GeoIpConfig).
    @Setter
    private static String geoIpDbPath = "/var/lib/GeoIP/GeoLite2-Country.mmdb";

    public enum DbType { COUNTRY, ASN }

    // DEFAULT: getCountry() is used: "represents the country where MaxMind believes the end user is located."
    // REGISTERED: getRegisteredCountry() is used: "represents the country where the ISP has registered a given IP block and may differ from the user's country."
    public enum CountryType { DEFAULT, REGISTERED }

    private static DatabaseReader getLookupService(final DbType type)
    {
        // Fast path: already initialized
        if (type == DbType.COUNTRY && countryLookupService != null) return countryLookupService;
        if (type == DbType.ASN && asnLookupService != null) return asnLookupService;

        final long now = System.currentTimeMillis();

        // Fast path: not initialized and still in backoff window
        if (type == DbType.COUNTRY && now < countryNextRetryAtMs) return null;
        if (type == DbType.ASN && now < asnNextRetryAtMs) return null;

        synchronized (LOOKUP_SERVICE_LOCK)
        {
            // Re-check after acquiring lock (another thread may have initialized)
            if (type == DbType.COUNTRY && countryLookupService != null) return countryLookupService;
            if (type == DbType.ASN && asnLookupService != null) return asnLookupService;

            // Re-check retry window under lock (another thread may have set it)
            final long nowLocked = System.currentTimeMillis();
            if (type == DbType.COUNTRY && nowLocked < countryNextRetryAtMs) return null;
            if (type == DbType.ASN && nowLocked < asnNextRetryAtMs) return null;

            try {
                switch (type) {
                    case COUNTRY: {
                        // CTU: configurable path (was hardcoded upstream)
                        File countryDb = new File(geoIpDbPath);
                        countryLookupService = new DatabaseReader.Builder(countryDb).build();
                        countryNextRetryAtMs = 0L; // success -> allow normal operation
                        return countryLookupService;
                    }
                    case ASN: {
                        File asnDb = new File("/var/lib/GeoIP/GeoLite2-ASN.mmdb");
                        asnLookupService = new DatabaseReader.Builder(asnDb).build();
                        asnNextRetryAtMs = 0L; // success
                        return asnLookupService;
                    }
                    default:
                        return null;
                }
            } catch (Exception e) {
                // set next retry time only for the failing DB
                if (type == DbType.COUNTRY) {
                    countryNextRetryAtMs = nowLocked + RETRY_DELAY_MS;
                    log.error("Maxmind GeoIP COUNTRY database could not be loaded; retry after {} minutes", RETRY_DELAY_MS / MS_PER_MINUTE, e);
                } else {
                    asnNextRetryAtMs = nowLocked + RETRY_DELAY_MS;
                    log.error("Maxmind GeoIP ASN database could not be loaded; retry after {} minutes", RETRY_DELAY_MS / MS_PER_MINUTE, e);
                }
                return null;
            }
        }
    }

    public static String lookupCountry(final InetAddress adr) {
        return lookupCountry(adr, CountryType.DEFAULT);
    }

    public static String lookupCountry(final InetAddress adr, final CountryType type) {
        try {
            final DatabaseReader lookupService = getLookupService(DbType.COUNTRY);
            if (lookupService == null) {
                return null;
            }

            final CountryResponse country = lookupService.country(adr);

            // If called with CountryType.REGISTERED -> return registered country, otherwise (default) the country based on GeoIP
            return (type == CountryType.REGISTERED)
                ? country.getRegisteredCountry().getIsoCode()
                : country.getCountry().getIsoCode();

        } catch (Exception e) {
            log.error("Error while looking up country for ip address: {}", adr, e);
            return null;
        }
    }

    public static class AsnInfo {
        public final Long autonomousSystemNumber;
        public final String autonomousSystemOrganization;

        public AsnInfo(final Long autonomousSystemNumber, final String autonomousSystemOrganization) {
            this.autonomousSystemNumber = autonomousSystemNumber;
            this.autonomousSystemOrganization = autonomousSystemOrganization;
        }
    }

    public static AsnInfo lookupAsn(final InetAddress adr) {
        try {
            final DatabaseReader lookupService = getLookupService(DbType.ASN);
            if (lookupService != null) {
                final AsnResponse asn = lookupService.asn(adr);
                return new AsnInfo(asn.getAutonomousSystemNumber(), asn.getAutonomousSystemOrganization());
            }
        } catch (Exception e) {
            log.error("Error while looking up ASN for ip address: {}", adr, e);
        }
        return null;
    }
}
