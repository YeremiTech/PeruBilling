# Outbox / Hibernate 7 regression fix

`OutboxEventEntity` inherits a JPA-generated UUID primary key from `BaseEntity`.
`OutboxPublisher` must not assign that primary key before `OutboxEventRepository.save(...)`.

Spring Data uses a non-null id as a signal that an entity may already exist and delegates to `merge(...)`.
With Hibernate 7, a brand-new generated-id entity passed through merge can be treated as detached and fail with `StaleObjectStateException` / `ObjectOptimisticLockingFailureException`.

The webhook envelope still receives its own random `eventId`, but the outbox storage row id is left null until JPA persistence assigns it. The two identifiers serve different purposes.
