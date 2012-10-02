# --- !Ups

alter table job add local_dir_name varchar(255);


# --- !Downs

alter table job drop local_dir_name;

