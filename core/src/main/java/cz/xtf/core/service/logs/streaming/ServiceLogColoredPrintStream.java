package cz.xtf.core.service.logs.streaming;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceLogColoredPrintStream extends PrintStream {
    private static final Logger logger = LoggerFactory.getLogger(ServiceLogColoredPrintStream.class);
    private final ServiceLogColor color;
    private final String prefix;
    private boolean needHeader = true;

    /**
     * Constructor has private access because you are supposed to used the
     * {@link Builder}
     */
    private ServiceLogColoredPrintStream(OutputStream out, ServiceLogColor color, String prefix) {
        // this class just writes to `out` so its life cycle, which must be handled at the outer scope, is not altered
        // at all
        super(out, true);
        this.color = color;
        this.prefix = prefix;
    }

    /**
     * Splits the current buffer into tokens; the criteria for splitting consists in considering each new line as a
     * token and everything else as another token.
     *
     * If we have e.g.: "AAAA\nBBBB", we would have 3 tokens: "AAAA","\n","BBBB";
     * Note that a buffer not containing any new line is considered a single token e.g. buffer "AAAA" is considered as
     * the single token "AAAA"
     * 
     * @param buf the data.
     * @param off the start offset in the data.
     * @param len the number of bytes to write.
     * @return the list of tokens in the data; joining the tokens you get the original data.
     * @throws IOException in case something goes wrong while managing streams internally
     */
    protected List<byte[]> getTokens(byte buf[], int off, int len) throws IOException {
        final List<byte[]> tokens = new ArrayList<>();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (int i = off; i < off + len; i++) {
                if ((char) buf[i] == '\n') {
                    if (baos.size() > 0) {
                        tokens.add(baos.toByteArray());
                        baos.reset();
                    }
                    tokens.add(Arrays.copyOfRange(buf, i, i + 1));
                } else {
                    baos.write(buf[i]);
                }
            }
            if (baos.size() > 0)
                tokens.add(baos.toByteArray());
        }
        if (tokens.isEmpty())
            tokens.add(Arrays.copyOfRange(buf, off, len));
        return tokens;
    }

    private boolean isNewLine(byte[] token) {
        return token != null && token.length == 1 && (char) token[0] == '\n';
    }

    /**
     * Prints the line header which usually consists of the name of the container the log comes from, preceded by the
     * name of the pod and the name of the namespace where the container is running, with properly colored background
     * and foreground
     * 
     * @throws IOException in case something goes wrong while writing to the underlying stream
     */
    private void printLineHeader() throws IOException {
        // set line header foreground and background
        out.write(color.value.getBytes(StandardCharsets.UTF_8));
        out.write(ServiceLogColor.ANSI_POD_NAME_BG.value.getBytes(StandardCharsets.UTF_8));
        // actual line header
        out.write(ServiceLogUtils.formatStreamedLogLine(prefix).getBytes(StandardCharsets.UTF_8));
        // reset foreground and background
        out.write(ServiceLogColor.ANSI_RESET_FG.value.getBytes(StandardCharsets.UTF_8));
        out.write(ServiceLogColor.ANSI_RESET_BG.value.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * Prints a token of the log line with properly colored background and foreground
     * 
     * @param buf the data.
     * @param off the start offset in the data.
     * @param len the number of bytes to write.
     * @throws IOException in case something goes wrong writing to the underlying stream
     */
    private void printToken(byte buf[], int off, int len) throws IOException {
        // set log token foreground and background
        out.write(ServiceLogColor.ANSI_POD_LOG_BG.value.getBytes(StandardCharsets.UTF_8));
        out.write(color.value.getBytes(StandardCharsets.UTF_8));
        // actual log token
        out.write(buf, off, len);
        // reset foreground and background
        out.write(ServiceLogColor.ANSI_RESET_FG.value.getBytes(StandardCharsets.UTF_8));
        out.write(ServiceLogColor.ANSI_RESET_BG.value.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public void write(int b) {
        write(new byte[] { (byte) b }, 0, 1);
    }

    /**
     * Prints the data with properly colored background and foreground; takes care of adding a line header to each line
     * 
     * @param buf the data.
     * @param off the start offset in the data.
     * @param len the number of bytes to write.
     */
    @Override
    public void write(byte buf[], int off, int len) {
        try {
            synchronized (out) {
                if (out == null)
                    throw new IOException("Stream is closed");

                logger.trace("TOKEN_FROM_CONTAINER: {}", new String(Arrays.copyOfRange(buf, off, len)));

                // first line ever --> print header
                if (needHeader) {
                    printLineHeader();
                    needHeader = false;
                }
                // split by newline
                List<byte[]> tokens = getTokens(buf, off, len);
                if (!tokens.isEmpty()) {
                    for (int i = 0; i < tokens.size(); i++) {
                        if (isNewLine(tokens.get(i))) {
                            out.write('\n');
                            // print the line header unless it's the last token: in that case set the reminder for the next
                            if (i < tokens.size() - 1) {
                                printLineHeader();
                            } else {
                                needHeader = true;
                            }
                        } else {
                            printToken(tokens.get(i), 0, tokens.get(i).length);
                        }
                    }
                }
            }
        } catch (InterruptedIOException x) {
            Thread.currentThread().interrupt();
        } catch (IOException x) {
            setError();
        }
    }

    public static class Builder {
        private OutputStream outputStream;
        private ServiceLogColor color;
        private String prefix;

        public Builder outputTo(final OutputStream outputStream) {
            this.outputStream = outputStream;
            return this;
        }

        public Builder withColor(final ServiceLogColor color) {
            this.color = color;
            return this;
        }

        public Builder withPrefix(final String prefix) {
            this.prefix = prefix;
            return this;
        }

        public ServiceLogColoredPrintStream build() {
            if (outputStream == null) {
                throw new IllegalStateException("OutputStream must be specified!");
            }
            if (color == null) {
                throw new IllegalStateException("Color must be specified!");
            }
            if (prefix == null) {
                throw new IllegalStateException("Prefix must be specified!");
            }
            return new ServiceLogColoredPrintStream(outputStream, color, prefix);
        }

    }

}
