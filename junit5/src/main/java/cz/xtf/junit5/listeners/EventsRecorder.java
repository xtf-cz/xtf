package cz.xtf.junit5.listeners;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.kubernetes.api.model.Event;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventsRecorder implements TestExecutionListener {
    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        final Path eventsLogPath = Paths.get("log", "events");
        final OpenShift openShift = OpenShifts.master();

        eventsLogPath.getParent().toFile().mkdirs();

        try (final Writer writer = new OutputStreamWriter(new FileOutputStream(eventsLogPath.toFile()),
                StandardCharsets.UTF_8)) {
            writer.append("LAST SEEN");
            writer.append('\t');
            writer.append("FIRST SEEN");
            writer.append('\t');
            writer.append("COUNT");
            writer.append('\t');
            writer.append("NAME");
            writer.append('\t');
            writer.append("KIND");
            writer.append('\t');
            writer.append("SUBOBJECT");
            writer.append('\t');
            writer.append("TYPE");
            writer.append('\t');
            writer.append("REASON");
            writer.append('\t');
            writer.append("SOURCE");
            writer.append('\t');
            writer.append("MESSAGE");

            writer.append('\n');

            for (Event event : openShift.getEvents()) {
                writer.append(event.getLastTimestamp());
                writer.append('\t');
                writer.append(event.getFirstTimestamp());
                writer.append('\t');
                writer.append("" + event.getCount());
                writer.append('\t');
                writer.append(event.getMetadata().getName());
                writer.append('\t');
                writer.append(event.getKind());
                writer.append('\t');
                writer.append(event.getInvolvedObject().getFieldPath());
                writer.append('\t');
                writer.append(event.getType());
                writer.append('\t');
                writer.append(event.getReason());
                writer.append('\t');
                writer.append(event.getSource().getComponent());
                writer.append('\t');
                writer.append(event.getMessage());

                writer.append('\n');
            }
        } catch (FileNotFoundException e) {
            log.warn("FileNotFoundException opening {}", eventsLogPath, e);
        } catch (IOException e) {
            log.warn("IOException writing {}", eventsLogPath, e);
        }
    }
}
