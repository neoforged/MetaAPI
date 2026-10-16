-- Records which scheduled slot (cron fire time) was last executed for each job.
-- Used by JobLockService to guarantee exactly-once-per-slot execution across replicas:
-- a lock can only be acquired if the row is free AND this slot has not been run yet.
ALTER TABLE job_lock ADD COLUMN IF NOT EXISTS last_run_at timestamptz;
