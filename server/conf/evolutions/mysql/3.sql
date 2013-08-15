# --- !Ups

alter table job change user user_id bigint;
alter table upload change user user_id bigint;
rename table user to users;

# --- !Downs

rename table users to user;
alter table upload change user_id user bigint;
alter table job change user_id user bigint;