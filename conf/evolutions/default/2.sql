# --- !Ups

create table job (
  id                        varchar(255) not null,
  nicename                  varchar(255),
  created                   datetime,
  started                   datetime,
  finished                  datetime,
  user                      bigint,
  guest_email               varchar(255),
  notified_created          tinyint(1) default 0,
  notified_complete         tinyint(1) default 0,
  constraint pk_job primary key (id))
;

alter table upload add user bigint;
alter table upload add job  varchar(255);


# --- !Downs

drop table job;

alter table upload drop user;
alter table upload drop job;

