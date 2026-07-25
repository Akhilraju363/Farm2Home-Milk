# Integration Testing Guide

This document describes the integration testing framework for Farm2Home Milk, which uses Testcontainers and PostgreSQL for comprehensive API flow testing.

## Overview

- **Framework**: Testcontainers with PostgreSQL 15 Alpine
- **Testing Style**: Spring Boot Integration Tests with MockMvc
- **Database**: Real PostgreSQL container per test class
- **Migration**: Flyway migrations run automatically

## Architecture

### Base Test Class

`BaseIntegrationTest` provides:
- Shared PostgreSQL container lifecycle management
- Dynamic property configuration (JDBC URL, username, password)
- Auto-configured MockMvc for servlet-based testing
- Flyway migration execution

### Test Helpers

`AuthenticationTestBuilder` provides:
- Builder pattern for authenticated requests
- Support for multiple roles (CUSTOMER, ADMIN, etc.)
- Automatic authority mapping

## Test Structure

### 1. Order Service Integration Tests
**File**: `backend/order-service/src/test/java/com/farm2home/order/integration/OrderServiceIntegrationTest.java`

Tests complete order workflows:
- Order creation with single/multiple milk types
- Order retrieval by ID and pagination
- Order status transitions (PENDING → CONFIRMED → COMPLETED)
- Validation of invalid state transitions

### 2. Payment Service Integration Tests
**File**: `backend/payment-service/src/test/java/com/farm2home/payment/integration/PaymentServiceIntegrationTest.java`

Tests payment processing and wallet management:
- Payment initiation via UPI and Card
- Payment callback processing (success/failure)
- Payment retrieval
- Wallet creation and top-up
- Balance validation

### 3. Delivery Service Integration Tests
**File**: `backend/delivery-service/src/test/java/com/farm2home/delivery/integration/DeliveryServiceIntegrationTest.java`

Tests delivery operations:
- Delivery partner registration
- Active partner filtering
- Delivery assignment creation
- Status transitions (ASSIGNED → PICKED_UP → DELIVERED)
- Failed delivery handling

### 4. End-to-End Workflow Tests
**File**: `backend/shared-libs/common-core/src/test/java/com/farm2home/core/test/integration/EndToEndWorkflowIntegrationTest.java`

Tests complete multi-service flows:
1. Customer creates order
2. Customer initiates payment
3. Payment gateway processes callback
4. Admin registers delivery partner
5. Admin creates delivery assignment
6. Partner picks up order
7. Partner delivers order
8. Order marked as completed

## Running the Tests

### Run all integration tests
```bash
mvn clean verify
```

### Run integration tests only
```bash
mvn clean verify -DskipUnitTests=true
```

### Run specific service tests
```bash
mvn -pl order-service clean verify
mvn -pl payment-service clean verify
mvn -pl delivery-service clean verify
```

### Run with debug logging
```bash
mvn clean verify -X -Dlogging.level.com.farm2home=DEBUG
```

## Test Database Setup

Each test class gets:
1. **New PostgreSQL container**: Isolated database for test class
2. **Flyway migrations**: Applied automatically before tests run
3. **Dynamic properties**: JDBC URL, credentials injected at runtime
4. **Data isolation**: `@BeforeEach` clears test data

**Note**: Containers are reusable (`withReuse(false)`) - set to `true` for faster local iteration.

## Authentication in Tests

### Customer Request
```java
var customerAuth = new AuthenticationTestBuilder()
    .withUserId(customerId)
    .withUsername("customer@farm2home.com")
    .withRoles("CUSTOMER");

mockMvc.perform(get("/api/v1/orders")
    .with(customerAuth.build()))
    .andExpect(status().isOk());
```

### Admin Request
```java
var adminAuth = new AuthenticationTestBuilder()
    .withUserId(UUID.randomUUID())
    .withUsername("admin@farm2home.com")
    .withRoles("ADMIN");

mockMvc.perform(post("/api/v1/delivery-partners")
    .with(adminAuth.build()))
    .andExpect(status().isCreated());
```

## Test Patterns

### 1. Setup and Cleanup
```java
@BeforeEach
void setUp() {
    customerId = UUID.randomUUID();
    authBuilder = new AuthenticationTestBuilder().withUserId(customerId);
    repository.deleteAll();  // Clear test data
}
```

### 2. Create and Verify
```java
var response = mockMvc.perform(post("/api/v1/resource")
    .contentType(MediaType.APPLICATION_JSON)
    .content(json))
    .andExpect(status().isCreated())
    .andReturn().getResponse().getContentAsString();

var savedEntity = repository.findAll();
assertThat(savedEntity).hasSize(1);
```

### 3. Multi-step Workflows
```java
// Create resource
var id = extractId(createResponse);

// Update resource
mockMvc.perform(patch("/api/v1/resource/" + id)
    .content(updateJson))
    .andExpect(status().isOk());

// Verify state change in database
var updated = repository.findById(id).orElseThrow();
assertThat(updated.getStatus()).isEqualTo(EXPECTED_STATUS);
```

## Database Migrations

Flyway migrations are executed during test startup. Ensure:
1. Migration files exist in `src/main/resources/db/migration/`
2. Schema matches production
3. Test users/data are seeded if needed

Example migration files:
- `V1__init.sql`: Initial schema
- `V2__add_indexes.sql`: Performance indexes
- `V3__seed_test_data.sql`: Optional test data

## Common Issues

### Issue: `Connection refused`
- Testcontainers requires Docker/Podman running
- Install Docker Desktop or use WSL2 on Windows

### Issue: `DDL statement fails`
- Check Flyway migration for syntax errors
- Ensure schema names match between migration and test

### Issue: `Timeout waiting for container`
- Increase timeout in BaseIntegrationTest if needed
- Use `withReuse(true)` for faster iteration

### Issue: `Tests fail intermittently`
- Check for data leakage between tests
- Ensure `@BeforeEach` clears repositories
- Watch for shared state in static containers

## Performance Considerations

1. **Container Startup**: ~5-10 seconds per test class
2. **Migration Execution**: Depends on schema complexity
3. **Database Cleanup**: Use indexed deletes for large tables
4. **Parallel Execution**: Testcontainers creates separate containers per class

## Best Practices

1. **Organize by service**: Keep tests in service-specific integration package
2. **Use builders**: Make test data setup reusable (see `AuthenticationTestBuilder`)
3. **Test workflows**: Don't just test endpoints in isolation
4. **Clear assertions**: Use `assertThat()` and `jsonPath()` for clarity
5. **Nested test classes**: Organize related tests with `@Nested`
6. **Meaningful test names**: Use `@DisplayName` for clarity

## Adding New Integration Tests

1. Create test class in `src/test/java/com/farm2home/SERVICE/integration/`
2. Extend `BaseIntegrationTest`
3. Inject `MockMvc` and repositories
4. Follow existing test patterns
5. Use `@Nested` to organize test groups

Example template:
```java
@DisplayName("New Service Integration Tests")
class NewServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private NewServiceRepository repository;
    
    @Nested
    @DisplayName("Feature Group")
    class FeatureTests {
        @Test
        @DisplayName("Should do something")
        void shouldDoSomething() throws Exception {
            // Test implementation
        }
    }
}
```

## Resources

- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Spring Boot Testing Guide](https://spring.io/guides/gs/testing-web/)
- [MockMvc Documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/test/web/servlet/MockMvc.html)
- [Flyway Migrations](https://flywaydb.org/)
