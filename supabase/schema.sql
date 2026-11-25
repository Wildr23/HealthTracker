-- GymTracker OS — Supabase PostgreSQL Schema (Free Tier Ready, Postgres 17+)
-- Applied to project HealthTracker. All synced tables enforce Row-Level Security ((select auth.uid()) = user_id).

-- 1. Exercises
create table if not exists public.exercises (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  slug text,
  name_en text not null,
  name_uk text not null,
  exercise_type text not null,
  primary_muscle text not null,
  secondary_muscles jsonb not null default '[]'::jsonb,
  calorie_tier text not null,
  jefit_eid int,
  jefit_name_match text,
  is_archived boolean not null default false,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create unique index if not exists idx_exercises_user_slug
  on public.exercises (user_id, slug)
  where slug is not null;

create index if not exists idx_exercises_user_id
  on public.exercises (user_id);

-- 2. Routines & Days
create table if not exists public.routines (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  source text not null,
  external_id text,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_routines_user_id
  on public.routines (user_id);

create table if not exists public.routine_days (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  routine_remote_id uuid not null references public.routines(remote_id) on delete cascade,
  name text not null,
  order_index int not null,
  external_id text,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_routine_days_user_id
  on public.routine_days (user_id);

create index if not exists idx_routine_days_routine_remote_id
  on public.routine_days (routine_remote_id);

create table if not exists public.routine_day_exercises (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  routine_day_remote_id uuid not null references public.routine_days(remote_id) on delete cascade,
  exercise_remote_id uuid not null references public.exercises(remote_id),
  order_index int not null,
  target_sets int not null,
  target_reps text,
  rest_seconds int,
  superset_group int,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_routine_day_exercises_user_id
  on public.routine_day_exercises (user_id);

create index if not exists idx_routine_day_exercises_day_id
  on public.routine_day_exercises (routine_day_remote_id);

create index if not exists idx_routine_day_exercises_ex_id
  on public.routine_day_exercises (exercise_remote_id);

-- 3. Workouts, Exercises, Sets
create table if not exists public.workouts (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  started_at timestamptz not null,
  ended_at timestamptz,
  routine_day_remote_id uuid references public.routine_days(remote_id) on delete set null,
  title text,
  source text not null,
  external_id text,
  notes text,
  took_pre_workout boolean not null default false,
  excluded_from_stats boolean not null default false,
  hc_status text not null default 'NOT_ATTEMPTED',
  hc_session_id text,
  hc_session_start timestamptz,
  hc_session_end timestamptz,
  hc_exercise_type text,
  kcal_watch double precision,
  kcal_smart_total double precision,
  avg_hr int,
  max_hr int,
  volume_kg double precision,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_workouts_user_started_at
  on public.workouts (user_id, started_at desc);

create index if not exists idx_workouts_routine_day_remote_id
  on public.workouts (routine_day_remote_id);

create table if not exists public.workout_exercises (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  workout_remote_id uuid not null references public.workouts(remote_id) on delete cascade,
  exercise_remote_id uuid not null references public.exercises(remote_id),
  order_index int not null,
  note text,
  superset_group int,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_workout_exercises_user_id
  on public.workout_exercises (user_id);

create index if not exists idx_workout_exercises_workout_id
  on public.workout_exercises (workout_remote_id);

create index if not exists idx_workout_exercises_ex_id
  on public.workout_exercises (exercise_remote_id);

create table if not exists public.workout_sets (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  workout_exercise_remote_id uuid not null references public.workout_exercises(remote_id) on delete cascade,
  set_index int not null,
  weight_kg double precision,
  reps int,
  rpe real,
  rir int,
  duration_sec int,
  distance_km double precision,
  calories double precision,
  set_type text not null default 'WORK',
  completed_at timestamptz,
  is_estimated_time boolean not null default false,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_workout_sets_user_id
  on public.workout_sets (user_id);

create index if not exists idx_workout_sets_we_id
  on public.workout_sets (workout_exercise_remote_id);

-- 4. Compressed HR & Calorie Streams ([[offsetSec, bpm], ...] < 1.5 KB per workout)
create table if not exists public.workout_hr_streams (
  workout_remote_id uuid primary key references public.workouts(remote_id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  hr_samples_json jsonb not null default '[]'::jsonb,
  calorie_buckets_json jsonb not null default '[]'::jsonb,
  updated_at timestamptz not null default now()
);

create index if not exists idx_workout_hr_streams_user_id
  on public.workout_hr_streams (user_id);

-- 5. Body Weight, Measurements & Xiaomi Scale Body Composition
create table if not exists public.body_weights (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  measured_date date not null,
  kg double precision not null,
  source text not null,
  fat_percent double precision,
  chest_cm double precision,
  arms_cm double precision,
  waist_cm double precision,
  hips_cm double precision,
  thighs_cm double precision,
  calves_cm double precision,
  shoulders_cm double precision,
  neck_cm double precision,
  forearms_cm double precision,
  flagged_outlier boolean not null default false,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint uq_body_weights_user_date_source unique (user_id, measured_date, source)
);

create index if not exists idx_body_weights_user_date
  on public.body_weights (user_id, measured_date desc);

create table if not exists public.scale_readings (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  measured_date date not null,
  captured_at timestamptz not null,
  weight_kg double precision not null,
  impedance_ohm int,
  bmi double precision not null,
  ideal_weight_kg double precision not null,
  bmr_kcal int not null,
  visceral_fat int not null,
  fat_percent double precision,
  lean_body_mass_kg double precision,
  water_percent double precision,
  muscle_mass_kg double precision,
  bone_mass_kg double precision,
  protein_percent double precision,
  metabolic_age int,
  body_type text,
  device_name text,
  mac_address text,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint uq_scale_readings_user_date unique (user_id, measured_date)
);

-- 6. Watch Activities & Synced User Preferences
create table if not exists public.watch_activities (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  hc_session_id text not null,
  started_at timestamptz not null,
  ended_at timestamptz not null,
  duration_sec int not null,
  title text,
  exercise_type text not null,
  origin_package text not null,
  distance_km double precision,
  active_kcal double precision,
  total_kcal double precision,
  avg_hr int,
  max_hr int,
  steps int,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint uq_watch_activities_user_session unique (user_id, hc_session_id)
);

create index if not exists idx_watch_activities_user_started
  on public.watch_activities (user_id, started_at desc);

create table if not exists public.user_preferences_sync (
  user_id uuid primary key references auth.users(id) on delete cascade,
  height_cm int not null default 182,
  birth_year int not null default 1993,
  is_male boolean not null default true,
  daily_step_goal int not null default 10000,
  hr_max int not null default 185,
  preferred_formula text not null default 'EPLEY',
  exercise_goals_json jsonb not null default '{}'::jsonb,
  injured_muscles_json jsonb not null default '{}'::jsonb,
  scale_min_weight_kg double precision,
  scale_max_weight_kg double precision,
  updated_at timestamptz not null default now()
);

-- 7. Phase 2-4 Forward-Compatible Tables (Supplements, Labs, Vitals, Events, Pain Map, Reports)
create table if not exists public.supplement_items (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name_en text not null,
  name_uk text not null,
  category text not null,
  default_dose double precision not null,
  unit text not null,
  caffeine_mg_per_serving int,
  servings_remaining double precision,
  low_stock_threshold double precision,
  cycle_days_on int,
  cycle_days_off int,
  cycle_start_date date,
  notes text,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_supplement_items_user_id
  on public.supplement_items (user_id);

create table if not exists public.supplement_schedules (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  supplement_remote_id uuid not null references public.supplement_items(remote_id) on delete cascade,
  trigger_type text not null,
  time_of_day text,
  offset_minutes int,
  days_of_week_mask int not null default 127,
  enabled boolean not null default true,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_supplement_schedules_user_id
  on public.supplement_schedules (user_id);

create index if not exists idx_supplement_schedules_supp_id
  on public.supplement_schedules (supplement_remote_id);

create table if not exists public.supplement_logs (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  supplement_remote_id uuid not null references public.supplement_items(remote_id) on delete cascade,
  taken_at timestamptz not null,
  dose double precision not null,
  caffeine_mg int,
  linked_workout_remote_id uuid references public.workouts(remote_id) on delete set null,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_supplement_logs_user_id
  on public.supplement_logs (user_id);

create index if not exists idx_supplement_logs_supp_id
  on public.supplement_logs (supplement_remote_id);

create index if not exists idx_supplement_logs_workout_id
  on public.supplement_logs (linked_workout_remote_id);

create table if not exists public.lab_reports (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  report_date date not null,
  lab_name text,
  notes text,
  attachment_path text,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_lab_reports_user_id
  on public.lab_reports (user_id);

create table if not exists public.lab_markers (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  lab_report_remote_id uuid not null references public.lab_reports(remote_id) on delete cascade,
  marker_code text not null,
  marker_name text not null,
  value double precision not null,
  unit text not null,
  lab_ref_min double precision,
  lab_ref_max double precision,
  athlete_opt_min double precision,
  athlete_opt_max double precision,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_lab_markers_user_id
  on public.lab_markers (user_id);

create index if not exists idx_lab_markers_report_id
  on public.lab_markers (lab_report_remote_id);

create table if not exists public.daily_vitals (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  vital_date date not null,
  sleep_minutes int,
  resting_hr int,
  hrv_ms double precision,
  spo2_avg double precision,
  wrist_temp_delta_c double precision,
  vascular_age int,
  stress_score int,
  steps int,
  distance_km double precision,
  active_kcal double precision,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint uq_daily_vitals_user_date unique (user_id, vital_date)
);

create table if not exists public.health_events (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  event_type text not null,
  title text not null,
  start_date date not null,
  end_date date,
  severity int,
  symptoms text,
  medications text,
  notes text,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_health_events_user_id
  on public.health_events (user_id);

create table if not exists public.body_pain_logs (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  logged_at timestamptz not null,
  body_zone text not null,
  pain_level int not null,
  note text,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_body_pain_logs_user_id
  on public.body_pain_logs (user_id);

create table if not exists public.periodic_reports (
  remote_id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  period_type text not null,
  start_date date not null,
  end_date date not null,
  metrics_json jsonb not null,
  ai_summary_markdown text not null,
  action_items_json jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists idx_periodic_reports_user_id
  on public.periodic_reports (user_id);

-- 8. Row-Level Security & Least-Privilege Grants
grant usage on schema public to authenticated;
revoke all on all tables in schema public from anon;
grant select, insert, update, delete on all tables in schema public to authenticated;

do $$
declare
  tbl text;
begin
  foreach tbl in array array[
    'exercises','routines','routine_days','routine_day_exercises',
    'workouts','workout_exercises','workout_sets','workout_hr_streams',
    'body_weights','scale_readings','watch_activities','user_preferences_sync',
    'supplement_items','supplement_schedules','supplement_logs','lab_reports','lab_markers',
    'daily_vitals','health_events','body_pain_logs','periodic_reports'
  ]
  loop
    execute format('alter table public.%I enable row level security;', tbl);
    execute format('drop policy if exists "User owns rows" on public.%I;', tbl);
    execute format(
      'create policy "User owns rows" on public.%I for all to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);',
      tbl
    );
  end loop;
end $$;
