package cz.xtf.junit5.extensions.helpers;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import cz.xtf.core.openshift.OpenShift;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.Route;

public class ResourcesTimestampHelper {

    static ZonedDateTime lastCreationTime(List<? extends HasMetadata> items) {
        ZonedDateTime result = null;
        for (HasMetadata item : items) {
            ZonedDateTime time = parseZonedDateTime(item.getMetadata().getCreationTimestamp());
            if (result == null || time.isAfter(result)) {
                result = time;
            }
        }
        return result == null ? ZonedDateTime.ofInstant(Instant.ofEpochSecond(0), ZoneOffset.UTC) : result;
    }

    // todo delete?
    static ZonedDateTime creationTime(HasMetadata item) {
        return ZonedDateTime.parse(item.getMetadata().getCreationTimestamp(), DateTimeFormatter.ISO_DATE_TIME);
    }

    static ZonedDateTime parseZonedDateTime(String time) {
        return ZonedDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME);
    }

    public static ZonedDateTime timeOfLastPod(OpenShift openShift) {
        return lastCreationTime(openShift.getPods());
    }

    public static ZonedDateTime timeOfLastDC(OpenShift openShift) {
        return lastCreationTime(openShift.getDeploymentConfigs());
    }

    public static ZonedDateTime timeOfLastBC(OpenShift openShift) {
        return lastCreationTime(openShift.getBuildConfigs());
    }

    public static ZonedDateTime timeOfLastBuild(OpenShift openShift) {
        return lastCreationTime(openShift.getBuilds());
    }

    public static ZonedDateTime timeOfLastIS(OpenShift openShift) {
        return lastCreationTime(openShift.getImageStreams());
    }

    public static ZonedDateTime timeOfLastSS(OpenShift openShift) {
        return lastCreationTime(openShift.getStatefulSets());
    }

    public static ZonedDateTime timeOfLastRoute(OpenShift openShift) {
        return lastCreationTime(openShift.getRoutes());
    }

    public static ZonedDateTime timeOfLastService(OpenShift openShift) {
        return lastCreationTime(openShift.getServices());
    }

    public static ZonedDateTime timeOfLastEvent(OpenShift openShift) {
        ZonedDateTime result = null;
        for (Event event : openShift.getEvents()) {
            if (event.getLastTimestamp() == null) {
                continue;
            }
            ZonedDateTime time = parseZonedDateTime(event.getLastTimestamp());
            if (result == null || (time != null && time.isAfter(result))) {
                result = time;
            }
        }
        return result == null ? ZonedDateTime.ofInstant(Instant.ofEpochSecond(0), ZoneOffset.UTC) : result;
    }

    public static ZonedDateTime timeOfLastResourceOf(OpenShift openShift, Class<? extends HasMetadata> resourceClass) {
        if (resourceClass.equals(Pod.class)) {
            return timeOfLastPod(openShift);
        } else if (resourceClass.equals(DeploymentConfig.class)) {
            return timeOfLastDC(openShift);
        } else if (resourceClass.equals(BuildConfig.class)) {
            return timeOfLastBC(openShift);
        } else if (resourceClass.equals(Build.class)) {
            return timeOfLastBuild(openShift);
        } else if (resourceClass.equals(ImageStream.class)) {
            return timeOfLastIS(openShift);
        } else if (resourceClass.equals(StatefulSet.class)) {
            return timeOfLastSS(openShift);
        } else if (resourceClass.equals(Route.class)) {
            return timeOfLastRoute(openShift);
        } else if (resourceClass.equals(Service.class)) {
            return timeOfLastService(openShift);
        } else if (resourceClass.equals(Event.class)) {
            return timeOfLastEvent(openShift);
        } else {
            throw new RuntimeException("Unsupported resource - " + resourceClass.getName());
        }
    }
}
