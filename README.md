# micro-chirp

## Purpose
A demonstration implementation of a highly available SNS application using Event Sourcing and CQRS

## Features

### Architecture
- Event Sourcing: Records all state changes as events, providing complete audit trail of data
- CQRS: Separates Read/Write operations, enabling optimized processing for each
- Materialized View + Delta Aggregation: Achieves near real-time read performance through PostgreSQL materialized views and application-layer delta aggregation

### Tech Stack
- Spring Boot + Kotlin
- PostgreSQL (with pg_cron extension)
- OpenAPI
- Locust (load testing)

### Functionality
- Anonymous login
- Post creation/deletion (with grapheme-based character limit)
- Timeline (global and user-specific)
- Like/Repost/Reply/View Count

### Performance Measurement
- Load testing with Locust
- Read/Write performance visualization