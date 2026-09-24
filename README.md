# Payment Service

A backend application for processing payments between user accounts.

The project focuses on building a production-style Java backend with authentication, transactional payment processing, concurrency handling and asynchronous event processing with Kafka.

## Tech Stack

* Java 25
* Spring Boot
* Spring Security + JWT
* Spring Data JPA / Hibernate
* PostgreSQL
* Flyway
* Apache Kafka
* Docker
* Testcontainers
* Maven

## Features

* User registration and authentication
* JWT-based authorization
* User accounts and balances
* Payments between accounts
* Transactional payment processing
* Pessimistic locking for concurrent payments
* REST API
* Database migrations with Flyway
* Integration tests with PostgreSQL and Testcontainers
* Asynchronous payment events with Kafka

## Architecture

```text
Client
  │
  ▼
REST API
  │
  ▼
Payment Service
  │
  ├── PostgreSQL
  │
  └── Kafka
       │
       └── Payment Events
```

## Running locally

Start the required services with Docker Compose and run the application using Maven Wrapper:

```bash
docker compose up -d
./mvnw spring-boot:run
```

Run tests:

```bash
./mvnw test
```
