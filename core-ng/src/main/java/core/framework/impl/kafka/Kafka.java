package core.framework.impl.kafka;

import core.framework.api.util.Maps;
import core.framework.api.util.StopWatch;
import core.framework.impl.log.LogManager;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author neo
 */
public class Kafka {
    public final ProducerMetrics producerMetrics;
    public final ConsumerMetrics consumerMetrics;
    private final Logger logger = LoggerFactory.getLogger(Kafka.class);
    private final String name;
    private final LogManager logManager;
    private final AtomicInteger consumerClientIdSequence = new AtomicInteger(1);
    public String uri;
    public MessageValidator validator = new MessageValidator();
    public Duration maxProcessTime = Duration.ofMinutes(15);
    public int maxPollRecords = 500;    // default kafka setting, refer to org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG
    private Producer<String, byte[]> producer;
    private KafkaMessageListener listener;

    public Kafka(String name, LogManager logManager) {
        this.name = name;
        this.logManager = logManager;
        this.producerMetrics = new ProducerMetrics(name);
        this.consumerMetrics = new ConsumerMetrics(name);
    }

    public Producer<String, byte[]> producer() {
        if (producer == null) {
            producer = createProducer();
        }
        return producer;
    }

    protected Producer<String, byte[]> createProducer() {
        if (uri == null) throw new Error("uri must not be null");
        StopWatch watch = new StopWatch();
        try {
            Map<String, Object> config = Maps.newHashMap();
            config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, uri);
            config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, Duration.ofSeconds(30).toMillis());  // metadata update timeout
            config.put(ProducerConfig.CLIENT_ID_CONFIG, producerClientId());
            Producer<String, byte[]> producer = new KafkaProducer<>(config, new StringSerializer(), new ByteArraySerializer());
            producerMetrics.setMetrics(producer.metrics());
            return producer;
        } finally {
            logger.info("create kafka producer, uri={}, name={}, elapsedTime={}", uri, name, watch.elapsedTime());
        }
    }

    public Consumer<String, byte[]> consumer(String group, Set<String> topics) {
        if (uri == null) throw new Error("uri must not be null");
        StopWatch watch = new StopWatch();
        try {
            Map<String, Object> config = Maps.newHashMap();
            config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, uri);
            config.put(ConsumerConfig.GROUP_ID_CONFIG, group);
            config.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 3 * 1024 * 1024); // get 3M message at max
            config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, (int) maxProcessTime.toMillis());
            config.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) maxProcessTime.plusSeconds(5).toMillis());
            config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
            String clientId = consumerClientId();
            config.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
            Consumer<String, byte[]> consumer = new KafkaConsumer<String, byte[]>(config, new StringDeserializer(), new ByteArrayDeserializer()) {
                @Override
                public void close() {
                    super.close();
                    consumerMetrics.removeMetrics(clientId);
                }
            };
            consumer.subscribe(topics);
            consumerMetrics.addMetrics(clientId, consumer.metrics());
            return consumer;
        } finally {
            logger.info("create kafka consumer, uri={}, name={}, topics={}, elapsedTime={}", uri, name, topics, watch.elapsedTime());
        }
    }

    private String producerClientId() {
        StringBuilder clientId = new StringBuilder("kafka-producer");
        if (name != null) clientId.append('-').append(name);
        clientId.append("-1");
        return clientId.toString();
    }

    private String consumerClientId() {
        StringBuilder clientId = new StringBuilder("kafka-consumer");
        if (name != null) clientId.append('-').append(name);
        clientId.append('-').append(consumerClientIdSequence.getAndIncrement());
        return clientId.toString();
    }

    public KafkaMessageListener listener() {
        if (listener == null) {
            listener = new KafkaMessageListener(this, name, logManager);
        }
        return listener;
    }

    public void initialize() {
        if (listener != null) listener.start();
    }

    public void close() {
        if (listener != null) listener.stop();
        if (producer != null) {
            logger.info("close kafka producer, uri={}", uri);
            producer.flush();
            producer.close();
        }
    }
}
