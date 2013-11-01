# --- !Ups

create table usersetting (
  id                        bigint auto_increment not null,
  user_id                   bigint,
  name                      varchar(255) not null,
  value                     varchar(255),
  constraint pk_setting primary key (id))
;

# --- !Downs

SET FOREIGN_KEY_CHECKS=0;

drop table usersetting;

SET FOREIGN_KEY_CHECKS=1;
