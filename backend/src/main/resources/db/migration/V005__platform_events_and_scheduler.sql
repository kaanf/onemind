-- platform: infrastructure tables owned by common (docs/architecture/DB_SCHEMA.md#platform).
-- DDL copied from the libraries, schema-qualified. When upgrading either library, compare with its
-- shipped schema and add a migration for any change.

-- Spring Modulith 2.2 event publication registry, v2 structure
-- (spring-modulith-events-jdbc: schemas/v2/schema-postgresql.sql).
create table platform.event_publication (
    id                     uuid                     not null,
    listener_id            text                     not null,
    event_type             text                     not null,
    serialized_event       text                     not null,
    publication_date       timestamp with time zone not null,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamp with time zone,
    primary key (id)
);
create index event_publication_serialized_event_hash_idx on platform.event_publication using hash (serialized_event);
create index event_publication_by_completion_date_idx on platform.event_publication (completion_date);

-- db-scheduler 17.1 (db-scheduler: src/test/resources/postgresql_tables.sql).
create table platform.scheduled_tasks (
    task_name            text                     not null,
    task_instance        text                     not null,
    task_data            bytea,
    execution_time       timestamp with time zone not null,
    picked               boolean                  not null,
    picked_by            text,
    last_success         timestamp with time zone,
    last_failure         timestamp with time zone,
    consecutive_failures int,
    last_heartbeat       timestamp with time zone,
    version              bigint                   not null,
    priority             smallint,
    primary key (task_name, task_instance)
);
create index execution_time_idx on platform.scheduled_tasks (execution_time);
create index last_heartbeat_idx on platform.scheduled_tasks (last_heartbeat);
create index priority_execution_time_idx on platform.scheduled_tasks (priority desc, execution_time asc);
