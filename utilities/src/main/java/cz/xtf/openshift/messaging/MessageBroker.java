package cz.xtf.openshift.messaging;

public interface MessageBroker {

	MessageBroker withQueues(String... queues);

	MessageBroker withTopics(String... topics);

}