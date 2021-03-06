/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ranger.audit.provider.kafka;

import java.util.Properties;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ranger.audit.model.AuditEventBase;
import org.apache.ranger.audit.model.AuthzAuditEvent;
import org.apache.ranger.audit.provider.BaseAuditProvider;
import org.apache.ranger.audit.provider.MiscUtil;

public class KafkaAuditProvider extends BaseAuditProvider {
	private static final Log LOG = LogFactory.getLog(KafkaAuditProvider.class);

	public static final String AUDIT_MAX_QUEUE_SIZE_PROP = "xasecure.audit.kafka.async.max.queue.size";
	public static final String AUDIT_MAX_FLUSH_INTERVAL_PROP = "xasecure.audit.kafka.async.max.flush.interval.ms";
	public static final String AUDIT_KAFKA_BROKER_LIST = "xasecure.audit.kafka.broker_list";
	public static final String AUDIT_KAFKA_TOPIC_NAME = "xasecure.audit.kafka.topic_name";
	boolean initDone = false;

	Producer<String, String> producer = null;
	String topic = null;

	@Override
	public void init(Properties props) {
		LOG.info("init() called");
		super.init(props);

		setMaxQueueSize(BaseAuditProvider.getIntProperty(props,
				AUDIT_MAX_QUEUE_SIZE_PROP, AUDIT_ASYNC_MAX_QUEUE_SIZE_DEFAULT));
		setMaxFlushInterval(BaseAuditProvider.getIntProperty(props,
				AUDIT_MAX_QUEUE_SIZE_PROP,
				AUDIT_ASYNC_MAX_FLUSH_INTERVAL_DEFAULT));
		topic = BaseAuditProvider.getStringProperty(props,
				AUDIT_KAFKA_TOPIC_NAME);
		if (topic == null || topic.isEmpty()) {
			topic = "ranger_audits";
		}

		try {
			if (!initDone) {
				String brokerList = BaseAuditProvider.getStringProperty(props,
						AUDIT_KAFKA_BROKER_LIST);
				if (brokerList == null || brokerList.isEmpty()) {
					brokerList = "localhost:9092";
				}

				Properties kakfaProps = new Properties();

				kakfaProps.put("metadata.broker.list", brokerList);
				kakfaProps.put("serializer.class",
						"kafka.serializer.StringEncoder");
				// kakfaProps.put("partitioner.class",
				// "example.producer.SimplePartitioner");
				kakfaProps.put("request.required.acks", "1");

				LOG.info("Connecting to Kafka producer using properties:"
						+ kakfaProps.toString());

				ProducerConfig kafkaConfig = new ProducerConfig(kakfaProps);
				producer = new Producer<String, String>(kafkaConfig);
				initDone = true;
			}
		} catch (Throwable t) {
			LOG.fatal("Error initializing kafka:", t);
		}
	}

	@Override
	public void log(AuditEventBase event) {
		if (event instanceof AuthzAuditEvent) {
			AuthzAuditEvent authzEvent = (AuthzAuditEvent) event;

			if (authzEvent.getAgentHostname() == null) {
				authzEvent.setAgentHostname(MiscUtil.getHostname());
			}

			if (authzEvent.getLogType() == null) {
				authzEvent.setLogType("RangerAudit");
			}

			if (authzEvent.getEventId() == null) {
				authzEvent.setEventId(MiscUtil.generateUniqueId());
			}
		}

		String message = MiscUtil.stringify(event);
		try {

			if (producer != null) {
				// TODO: Add partition key
				KeyedMessage<String, String> keyedMessage = new KeyedMessage<String, String>(
						topic, message);
				producer.send(keyedMessage);
			} else {
				LOG.info("AUDIT LOG (Kafka Down):" + message);
			}
		} catch (Throwable t) {
			LOG.error("Error sending message to Kafka topic. topic=" + topic
					+ ", message=" + message, t);
		}
	}

	@Override
	public void start() {
		LOG.info("start() called");
		// TODO Auto-generated method stub

	}

	@Override
	public void stop() {
		LOG.info("stop() called");
		if (producer != null) {
			try {
				producer.close();
			} catch (Throwable t) {
				LOG.error("Error closing Kafka producer");
			}
		}
	}

	@Override
	public void waitToComplete() {
		LOG.info("waitToComplete() called");
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isFlushPending() {
		LOG.info("isFlushPending() called");
		return false;
	}

	@Override
	public long getLastFlushTime() {
		LOG.info("getLastFlushTime() called");

		return 0;
	}

	@Override
	public void flush() {
		LOG.info("flush() called");

	}

	public boolean isAsync() {
		return true;
	}

}
