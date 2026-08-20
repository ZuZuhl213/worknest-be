CREATE TABLE qrtz_job_details (
    sched_name VARCHAR(120) NOT NULL, job_name VARCHAR(200) NOT NULL, job_group VARCHAR(200) NOT NULL,
    description VARCHAR(250), job_class_name VARCHAR(250) NOT NULL, is_durable BOOLEAN NOT NULL,
    is_nonconcurrent BOOLEAN NOT NULL, is_update_data BOOLEAN NOT NULL, requests_recovery BOOLEAN NOT NULL,
    job_data BYTEA, PRIMARY KEY (sched_name, job_name, job_group)
);
CREATE TABLE qrtz_triggers (
    sched_name VARCHAR(120) NOT NULL, trigger_name VARCHAR(200) NOT NULL, trigger_group VARCHAR(200) NOT NULL,
    job_name VARCHAR(200) NOT NULL, job_group VARCHAR(200) NOT NULL, description VARCHAR(250),
    next_fire_time BIGINT, prev_fire_time BIGINT, priority INTEGER, trigger_state VARCHAR(16) NOT NULL,
    trigger_type VARCHAR(8) NOT NULL, start_time BIGINT NOT NULL, end_time BIGINT, calendar_name VARCHAR(200),
    misfire_instr SMALLINT, job_data BYTEA, PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, job_name, job_group) REFERENCES qrtz_job_details(sched_name, job_name, job_group)
);
CREATE INDEX idx_qrtz_t_nft_st ON qrtz_triggers(sched_name, next_fire_time, trigger_state);
CREATE TABLE qrtz_simple_triggers (
    sched_name VARCHAR(120) NOT NULL, trigger_name VARCHAR(200) NOT NULL, trigger_group VARCHAR(200) NOT NULL,
    repeat_count BIGINT NOT NULL, repeat_interval BIGINT NOT NULL, times_triggered BIGINT NOT NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group), FOREIGN KEY (sched_name, trigger_name, trigger_group)
    REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);
CREATE TABLE qrtz_cron_triggers (
    sched_name VARCHAR(120) NOT NULL, trigger_name VARCHAR(200) NOT NULL, trigger_group VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL, time_zone_id VARCHAR(80),
    PRIMARY KEY (sched_name, trigger_name, trigger_group), FOREIGN KEY (sched_name, trigger_name, trigger_group)
    REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);
CREATE TABLE qrtz_simprop_triggers (
    sched_name VARCHAR(120) NOT NULL, trigger_name VARCHAR(200) NOT NULL, trigger_group VARCHAR(200) NOT NULL,
    str_prop_1 VARCHAR(512), str_prop_2 VARCHAR(512), str_prop_3 VARCHAR(512), int_prop_1 INTEGER,
    int_prop_2 INTEGER, long_prop_1 BIGINT, long_prop_2 BIGINT, dec_prop_1 NUMERIC(13,4), dec_prop_2 NUMERIC(13,4),
    bool_prop_1 BOOLEAN, bool_prop_2 BOOLEAN, PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);
CREATE TABLE qrtz_blob_triggers (
    sched_name VARCHAR(120) NOT NULL, trigger_name VARCHAR(200) NOT NULL, trigger_group VARCHAR(200) NOT NULL,
    blob_data BYTEA, PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);
CREATE TABLE qrtz_calendars (
    sched_name VARCHAR(120) NOT NULL, calendar_name VARCHAR(200) NOT NULL, calendar BYTEA NOT NULL,
    PRIMARY KEY (sched_name, calendar_name)
);
CREATE TABLE qrtz_paused_trigger_grps (sched_name VARCHAR(120) NOT NULL, trigger_group VARCHAR(200) NOT NULL,
    PRIMARY KEY (sched_name, trigger_group));
CREATE TABLE qrtz_fired_triggers (
    sched_name VARCHAR(120) NOT NULL, entry_id VARCHAR(140) NOT NULL, trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL, instance_name VARCHAR(200) NOT NULL, fired_time BIGINT NOT NULL,
    sched_time BIGINT NOT NULL, priority INTEGER NOT NULL, state VARCHAR(16) NOT NULL, job_name VARCHAR(200),
    job_group VARCHAR(200), is_nonconcurrent BOOLEAN, requests_recovery BOOLEAN,
    PRIMARY KEY (sched_name, entry_id));
CREATE TABLE qrtz_scheduler_state (sched_name VARCHAR(120) NOT NULL, instance_name VARCHAR(200) NOT NULL,
    last_checkin_time BIGINT NOT NULL, checkin_interval BIGINT NOT NULL, PRIMARY KEY (sched_name, instance_name));
CREATE TABLE qrtz_locks (sched_name VARCHAR(120) NOT NULL, lock_name VARCHAR(40) NOT NULL,
    PRIMARY KEY (sched_name, lock_name));
INSERT INTO qrtz_locks(sched_name, lock_name) VALUES ('WorkNestScheduler', 'TRIGGER_ACCESS'), ('WorkNestScheduler', 'JOB_ACCESS'),
('WorkNestScheduler', 'CALENDAR_ACCESS'), ('WorkNestScheduler', 'STATE_ACCESS'), ('WorkNestScheduler', 'MISFIRE_ACCESS');
