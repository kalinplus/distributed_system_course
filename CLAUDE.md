# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a distributed systems course project - a simplified e-commerce system with "user-product-inventory-order" business flow. It demonstrates high-concurrency reads, distributed caching, read-write separation, and load balancing.

## Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.2.x
- **ORM**: MyBatis-Plus 3.5.x
- **Database**: MySQL 8.0 (master-slave architecture)
- **Cache**: Redis 7.2
- **Gateway**: Nginx 1.24
- **Auth**: JWT (jjwt 0.12.3)
- **Build**: Maven 3.9

## Architecture

4 microservices:
- **User Service**: registration, login, user info
- **Product Service**: product list, details, management
- **Inventory Service**: stock query, deduction
- **Order Service**: order creation, query, status management

## Development Commands

```bash
# Build the project
mvn clean package -DskipTests

# Run the application
java -jar target/ecommerce-system-1.0.0.jar

# Run tests
mvn test

# Run a single test
mvn test -Dtest=ClassName#methodName
```

## Project Structure (to be created)

```
ecommerce-system/
├── src/main/java/com/course/ecommerce/
│   ├── EcommerceApplication.java
│   ├── config/           # Configuration classes
│   ├── controller/       # REST controllers
│   ├── service/          # Business logic
│   ├── mapper/           # Data access (MyBatis)
│   ├── entity/          # Domain entities
│   ├── dto/              # Data transfer objects
│   ├── common/           # Shared components
│   └── interceptor/     # Request interceptors
├── src/main/resources/
│   ├── application.yml  # App configuration
│   └── mapper/           # MyBatis XML
└── pom.xml
```

## Key Configuration

- MySQL Master: port 3306
- MySQL Slave: port 3307
- Redis: port 6379
- Application: port 8080

## Documentation

See `/docs/` folder:
- `architecture.md` - System architecture and service design
- `api.md` - RESTful API definitions
- `database.md` - ER diagram and table schemas
- `tech-stack.md` - Technology stack details
