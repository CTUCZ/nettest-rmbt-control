package at.rtr.rmbt.config;

import at.rtr.rmbt.properties.IntegrityProperties;
import at.rtr.rmbt.service.IntegrityVerdictClient;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IntegrityConfigTest {

    private final IntegrityConfig config = new IntegrityConfig();
    private final IntegrityProperties properties = new IntegrityProperties();

    @SuppressWarnings("unchecked")
    private final ObjectProvider<IntegrityVerdictClient> clientProvider = mock(ObjectProvider.class);

    private ListAppender<ILoggingEvent> logAppender;

    @Before
    public void setUp() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(IntegrityConfig.class)).addAppender(logAppender);
    }

    @After
    public void tearDown() {
        ((Logger) LoggerFactory.getLogger(IntegrityConfig.class)).detachAppender(logAppender);
    }

    @Test
    public void integrityStartupLogger_whenDisabled_expectDisabledInfoLog() {
        // Given
        properties.setEnabled(false);

        // When
        config.integrityStartupLogger(properties, clientProvider).afterSingletonsInstantiated();

        // Then
        ILoggingEvent event = singleEvent();
        assertEquals(Level.INFO, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("DISABLED"));
    }

    @Test
    public void integrityStartupLogger_whenEnabledWithoutUsableCredentials_expectOffWarnLog() {
        // Given: enabled=true but the verdict client bean is absent (blank/unreadable credentials)
        properties.setEnabled(true);
        when(clientProvider.getIfAvailable()).thenReturn(null);

        // When
        config.integrityStartupLogger(properties, clientProvider).afterSingletonsInstantiated();

        // Then
        ILoggingEvent event = singleEvent();
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("OFF"));
        assertTrue(event.getFormattedMessage().contains("credentials"));
    }

    @Test
    public void integrityStartupLogger_whenEnabledAndClientPresent_expectEnabledInfoLogWithConfiguration() {
        // Given
        properties.setEnabled(true);
        when(clientProvider.getIfAvailable()).thenReturn(mock(IntegrityVerdictClient.class));

        // When
        config.integrityStartupLogger(properties, clientProvider).afterSingletonsInstantiated();

        // Then: one INFO line with the operationally relevant configuration
        ILoggingEvent event = singleEvent();
        assertEquals(Level.INFO, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.contains("ENABLED"));
        assertTrue(message.contains("cz.ctu.rmbt.android.prod"));
        assertTrue(message.contains("certified=monitor"));
        assertTrue(message.contains("regular=monitor"));
        assertTrue(message.contains("certificate check SKIPPED"));
    }

    @Test
    public void integrityStartupLogger_whenCertificateDigestsConfigured_expectCertificateCheckActive() {
        // Given
        properties.setEnabled(true);
        properties.getCertificateDigests().add("digest");
        when(clientProvider.getIfAvailable()).thenReturn(mock(IntegrityVerdictClient.class));

        // When
        config.integrityStartupLogger(properties, clientProvider).afterSingletonsInstantiated();

        // Then
        assertTrue(singleEvent().getFormattedMessage().contains("certificate check active"));
    }

    private ILoggingEvent singleEvent() {
        assertEquals(1, logAppender.list.size());
        return logAppender.list.get(0);
    }
}