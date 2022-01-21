package cz.xtf.core.service.logs.streaming;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ServiceLogColoredPrintStreamTest {
    /**
     * We test that the routine that identifies new lines in the data to print, works correctly
     */
    @Test
    public void testGetTokens() throws IOException {
        ServiceLogColoredPrintStream coloredPrintStream = new ServiceLogColoredPrintStream.Builder()
                .outputTo(System.out)
                .withColor(ServiceLogColor.getNext())
                .withPrefix("TEST")
                .build();
        // 7 tokens
        byte[] buf = "\nAAAA\nBBBB\nCCCC\n".getBytes(StandardCharsets.UTF_8);
        List<byte[]> tokens = coloredPrintStream.getTokens(buf, 0, buf.length);
        Assertions.assertEquals(tokens.size(), 7);
        // offset other than 0
        buf = "\nAAAA\nBBBB\nCCCC\n".getBytes(StandardCharsets.UTF_8);
        tokens = coloredPrintStream.getTokens(buf, 1, buf.length - 1);
        Assertions.assertEquals(tokens.size(), 6);
        // offset set to second token, length set to second-last token
        buf = "\nAAAA\nBBBB\nCCCC\n".getBytes(StandardCharsets.UTF_8);
        tokens = coloredPrintStream.getTokens(buf, 1, buf.length - 2);
        Assertions.assertEquals(tokens.size(), 5);
        // offset set to third token, length set to second-last token
        buf = "\nAAAA\nBBBB\nCCCC\n".getBytes(StandardCharsets.UTF_8);
        tokens = coloredPrintStream.getTokens(buf, 5, buf.length - 7);
        Assertions.assertEquals(tokens.size(), 4);
        // just one non new-line token
        buf = "AAAA".getBytes(StandardCharsets.UTF_8);
        tokens = coloredPrintStream.getTokens(buf, 0, buf.length);
        Assertions.assertEquals(tokens.size(), 1);
        // just one new-line token
        buf = "\n".getBytes(StandardCharsets.UTF_8);
        tokens = coloredPrintStream.getTokens(buf, 0, buf.length);
        Assertions.assertEquals(tokens.size(), 1);
        // two new-line tokens
        buf = "\n\n".getBytes(StandardCharsets.UTF_8);
        tokens = coloredPrintStream.getTokens(buf, 0, buf.length);
        Assertions.assertEquals(tokens.size(), 2);
    }
}
