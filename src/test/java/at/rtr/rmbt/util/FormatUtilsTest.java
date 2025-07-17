package at.rtr.rmbt.util;

import at.rtr.rmbt.utils.FormatUtils;
import org.junit.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class FormatUtilsTest {

    @Test
    public void testSpeedFormatting() {
        String s = FormatUtils.formatSpeed(111, Locale.US);
        assertEquals("0.11",s);

        s = FormatUtils.formatSpeed(1111, Locale.US);
        assertEquals("1.11",s);

        s = FormatUtils.formatSpeed(11111, Locale.US);
        assertEquals("11.1",s);

        s = FormatUtils.formatSpeed(111111, Locale.US);
        assertEquals("111",s);

        s = FormatUtils.formatSpeed(1111111, Locale.US);
        assertEquals("1,111",s);

        s = FormatUtils.formatSpeed(11111111, Locale.US);
        assertEquals("11,111",s);
    }

}
