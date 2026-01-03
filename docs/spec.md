## Technical Requirements & Specifications

### Tech Stack
- Backend: Spring Boot + Kotlin
- Database: PostgreSQL (with pg_cron extension)
- API Definition: OpenAPI
- Load Testing: Locust

### Architecture Requirements
- Pattern: Event Sourcing + CQRS
- DB Configuration: Single PostgreSQL instance
- Write Side: Append-only to event store
- Read Side: Materialized views + delta event aggregation
- View Update: Periodic refresh via pg_cron

### Authentication Requirements
- Anonymous Login (UUID generation)
- No Firebase, self-contained within Spring Boot

### Functional Requirements

#### Post Features
- Post creation (text only)
- Post deletion
- Character limit (validated with grapheme counter)

#### Timeline Features
- Global timeline
- User-specific post list
- Pagination support

#### Social Features
- Like/Unlike
- Repost/Unrepost
- Reply (referenced via reply_to_post_id)
- View Count

#### Out of Scope
- Search
- Notifications
- Frontend

### Event Sourcing Design

#### Event Definitions

**Post Events**
- Post creation: post ID, user ID, content
- Post deletion: post ID, user ID

**Reply Events**
- Reply creation: reply post ID, reply_to_post_id, user ID, content
- Reply deletion: reply post ID, user ID

**Like Events**
- Like: post ID, user ID
- Unlike: post ID, user ID

**Repost Events**
- Repost: post ID, user ID
- Unrepost: post ID, user ID

**View Events**
- View: post ID, user ID

#### Aggregations

**Global Timeline**
- Aggregate post creation events in chronological order
- Exclude deleted posts based on post deletion events
- Pagination support

**User-Specific Post List**
- Aggregate post creation events for specific user
- Exclude deleted posts based on post deletion events
- Pagination support

**Post Details**
- Basic info: post creation event
- Like count: Like events - Unlike events
- Repost count: Repost events - Unrepost events
- Reply count: Reply creation events for this post (excluding deleted)
- View count: View events
- Current user's like status: latest Like/Unlike event
- Current user's repost status: latest Repost/Unrepost event

**Reply List**
- Aggregate reply creation events for specific post
- Exclude deleted replies based on reply deletion events
- Each reply's Like/Repost/Reply/View counts aggregated similarly

### Database Design Requirements

#### Event Store
- Single event table (storing all event types)
- Append-only (no updates or deletes)

#### Materialized Views
- Timeline view
- User-specific post view
- Post aggregation view
- Periodic refresh via pg_cron

#### Read Model Update Strategy
- Record last update timestamp of materialized views
- On read: aggregate view + events since last update timestamp
- Application layer constructs latest state

### API Requirements
- Defined with OpenAPI
- RESTful design

### Load Testing Requirements
- Use Locust
- Write performance measurement (various event generation)
- Read performance measurement (various aggregation queries)

### High Availability Requirements
- Data restoration capability via Event Sourcing
- Read/Write separation via CQRS
- Horizontally scalable design