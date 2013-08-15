# --- !Ups

alter table upload add browser_id bigint;


# --- !Downs

alter table upload drop browser_id;

