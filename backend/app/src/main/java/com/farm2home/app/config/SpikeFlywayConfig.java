package com.farm2home.app.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

/**
 * ONE Flyway instance per aggregated schema — the mechanic proven by the spike, extended
 * group-by-group.
 *
 * <p>Every service ships its migrations at the <em>same</em> classpath location
 * ({@code classpath:db/migration}) with overlapping versions ({@code V1__…}, {@code V2__…}, …),
 * so a single Flyway pointed there would see the union and collide. Spring Boot's single
 * auto-configured Flyway is off ({@code spring.flyway.enabled=false}); instead there is one
 * {@link Flyway} bean per schema, each pointed at a distinct
 * {@code classpath:db/migration/<schema>} location holding <b>byte-for-byte copies</b> of that
 * service's own {@code db/migration/*.sql} (checksums are content-only, so the copies validate
 * cleanly against the existing {@code <schema>.flyway_schema_history}). Each service module
 * keeps its originals, so it still runs standalone.
 *
 * <pre>
 *   Group 1: farmFlyway (farm), productionFlyway (production)
 *   Group 2: customerFlyway (customer), inventoryFlyway (inventory)
 *   Group 3: subscriptionFlyway (subscription), orderFlyway ("order" — reserved word,
 *            quoted by Flyway; entities use @Table(schema="`order`"))
 *   Group 4: paymentFlyway (payment), deliveryFlyway (delivery)
 *   Group 5: notificationFlyway (notification), invoiceFlyway (invoice)
 * </pre>
 *
 * <p>{@link #entityManagerFactoryDependsOnFlyway} makes Hibernate ({@code ddl-auto: validate})
 * wait for every migration to finish before it validates the mapped entities.
 */
@Configuration
public class SpikeFlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(SpikeFlywayConfig.class);

    private static Flyway schemaFlyway(DataSource dataSource, String schema) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                // subscription/order-service declare baseline-on-migrate in their own yml; harmless
                // for the others (no-op once a flyway_schema_history row exists, which is the case
                // for every schema here).
                .baselineOnMigrate(true)
                .locations("classpath:db/migration/" + schema)
                .load();
    }

    // ---- Group 1 ----
    @Bean(initMethod = "migrate")
    public Flyway farmFlyway(DataSource dataSource) {
        return schemaFlyway(dataSource, "farm");
    }

    @Bean(initMethod = "migrate")
    public Flyway productionFlyway(DataSource dataSource) {
        return schemaFlyway(dataSource, "production");
    }

    // ---- Group 2 ----
    @Bean(initMethod = "migrate")
    public Flyway customerFlyway(DataSource dataSource) {
        return schemaFlyway(dataSource, "customer");
    }

    @Bean(initMethod = "migrate")
    public Flyway inventoryFlyway(DataSource dataSource) {
        return schemaFlyway(dataSource, "inventory");
    }

    // ---- Group 3 ----
    @Bean(initMethod = "migrate")
    public Flyway subscriptionFlyway(DataSource dataSource) {
        return schemaFlyway(dataSource, "subscription");
    }

    @Bean(initMethod = "migrate")
    public Flyway orderFlyway(DataSource dataSource) {
        return schemaFlyway(dataSource, "order");
    }

    // ---- Group 4 ----
    @Bean(initMethod = "migrate")
    public Flyway paymentFlyway(DataSource dataSource) {
        return schemaFlyway(dataSource, "payment");
    }

    @Bean(initMethod = "migrate")
    public Flyway deliveryFlyway(DataSource dataSource) {
        return schemaFlyway(dataSource, "delivery");
    }

    // ---- Group 5 ----
    @Bean(initMethod = "migrate")
    public Flyway notificationFlyway(DataSource dataSource) {
        return schemaFlyway(dataSource, "notification");
    }

    @Bean(initMethod = "migrate")
    public Flyway invoiceFlyway(DataSource dataSource) {
        return schemaFlyway(dataSource, "invoice");
    }

    /**
     * Marker bean that depends on every Flyway bean, so the EntityManagerFactory (wired below to
     * depend on {@code spikeFlywayMigrations}) is guaranteed to see all {@code migrate()} calls
     * completed before Hibernate validates.
     */
    @Bean
    public SpikeFlywayMigrations spikeFlywayMigrations(Flyway farmFlyway, Flyway productionFlyway,
                                                      Flyway customerFlyway, Flyway inventoryFlyway,
                                                      Flyway subscriptionFlyway, Flyway orderFlyway,
                                                      Flyway paymentFlyway, Flyway deliveryFlyway,
                                                      Flyway notificationFlyway, Flyway invoiceFlyway) {
        List<Flyway> all = List.of(farmFlyway, productionFlyway, customerFlyway, inventoryFlyway,
                subscriptionFlyway, orderFlyway, paymentFlyway, deliveryFlyway,
                notificationFlyway, invoiceFlyway);
        all.forEach(f -> log.info("Flyway applied — schema={} location={}",
                f.getConfiguration().getDefaultSchema(),
                String.join(",", f.getConfiguration().getLocations()[0].toString())));
        return new SpikeFlywayMigrations(all);
    }

    @Bean
    public static EntityManagerFactoryDependsOnPostProcessor entityManagerFactoryDependsOnFlyway() {
        return new EntityManagerFactoryDependsOnPostProcessor("spikeFlywayMigrations");
    }

    /** Holds every Flyway instance so tests can assert the count. */
    public record SpikeFlywayMigrations(List<Flyway> instances) {
    }
}
