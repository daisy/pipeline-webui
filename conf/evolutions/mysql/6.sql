# --- !Ups

alter table job add script_id varchar(255);
alter table job add script_name varchar(255);


# --- !Downs

alter table job drop script_id;
alter table job drop script_name;

