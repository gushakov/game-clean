package com.github.gameclean.infrastructure.transaction;

import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Explicit wiring for transaction demarcation — all of it in one visible place rather than scattered
 * component-scan annotations. Declares the two {@link TransactionTemplate}s (read-write and
 * read-only) and the {@link SpringTransactionAdapter} that backs the
 * {@link TransactionOperationsOutputPort}.
 *
 * <p>The {@link PlatformTransactionManager} is Spring Boot's autoconfigured manager (a
 * {@code JdbcTransactionManager}, present once a {@code DataSource} is on the context).
 */
@Configuration
public class TransactionConfig {

    @Bean
    @Primary
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    @Qualifier("read-only")
    public TransactionTemplate readOnlyTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    @Bean
    public TransactionOperationsOutputPort transactionOperations(
            TransactionTemplate transactionTemplate,
            @Qualifier("read-only") TransactionTemplate readOnlyTransactionTemplate) {
        return new SpringTransactionAdapter(transactionTemplate, readOnlyTransactionTemplate);
    }
}
