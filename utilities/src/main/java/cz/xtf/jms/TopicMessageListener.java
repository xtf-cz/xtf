package cz.xtf.jms;

import java.util.ArrayList;
import java.util.List;

import javax.jms.Message;
import javax.jms.MessageListener;

public class TopicMessageListener implements MessageListener {

	private String topicName;

	private List<Message> messageRecieved = new ArrayList<Message>();

	public TopicMessageListener(String topicName) {
		this.topicName = topicName;
	}

	public String getTopicName() {
		return topicName;
	}

	public void setTopicName(String topicName) {
		this.topicName = topicName;
	}

	public Message getMessageRecieved() {
		if (messageRecieved.size() > 0) {
			return messageRecieved.get(messageRecieved.size() - 1);
		} else {
			return null;
		}
	}

	public void onMessage(Message msg) {
		messageRecieved.add(msg);
	}

	public List<Message> getAllReceivedMessages() {
		return this.messageRecieved;
	}

}
