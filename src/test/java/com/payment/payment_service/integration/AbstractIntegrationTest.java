package com.payment.payment_service.integration;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import com.payment.payment_service.config.TestRedisConfig;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_service.auth.service.JwtService;
import com.payment.payment_service.shared.repository.OutboxRepository;
import com.payment.payment_service.transaction.repository.TransactionRepository;
import com.payment.payment_service.transfer.repository.TransferRepository;
import com.payment.payment_service.transfer.service.CreateTransferService;
import com.payment.payment_service.user.repository.UserRepository;
import com.payment.payment_service.wallet.repository.DepositRepository;
import com.payment.payment_service.wallet.repository.ProcessedTransferRepository;
import com.payment.payment_service.wallet.repository.WalletRepository;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings({"resource", "deprecation"})
public abstract class AbstractIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withSharedMemorySize(256 * 1024 * 1024L);

    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    ).withEnv("KAFKA_HEAP_OPTS", "-Xmx256m -Xms128m");

    static GenericContainer<?> redis = new GenericContainer<>(
        DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    static {
        postgres.start();
        kafka.start();
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected WalletRepository walletRepository;

    @Autowired
    protected TransferRepository transferRepository;

    @Autowired
    protected TransactionRepository transactionRepository;

    @Autowired
    protected OutboxRepository outboxRepository;

    @Autowired
    protected DepositRepository depositRepository;

    @Autowired
    protected ProcessedTransferRepository processedTransferRepository;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected CreateTransferService createTransferService;

    @Autowired
    protected TestHelper testHelper;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAllInBatch();
        processedTransferRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        transferRepository.deleteAllInBatch();
        depositRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
}
