begin;

create or replace function public.media_orphan_candidates(
  p_cutoff timestamptz default now() - interval '24 hours',
  p_limit integer default 100
)
returns table(bucket_id text, name text)
language sql
security definer
set search_path = ''
as $$
  select o.bucket_id, o.name
  from storage.objects o
  where o.bucket_id in ('rtc-community-media', 'civic-report-evidence')
    and o.created_at < coalesce(p_cutoff, now() - interval '24 hours')
    and not exists (
      select 1 from public.community_post_media m
      where o.bucket_id = 'rtc-community-media' and m.storage_path = o.name
    )
    and not exists (
      select 1 from public.civic_report_evidence e
      where o.bucket_id = 'civic-report-evidence' and e.storage_path = o.name
    )
    and not (
      o.bucket_id = 'rtc-community-media'
      and exists (
        select 1
        from public.community_posts p
        where p.id::text = split_part(o.name, '/', 2)
          and p.state = 'DRAFT'
      )
    )
  order by o.created_at asc
  limit greatest(1, least(coalesce(p_limit, 100), 500));
$$;

revoke all on function public.media_orphan_candidates(timestamptz, integer) from public, anon, authenticated;
grant execute on function public.media_orphan_candidates(timestamptz, integer) to service_role;

select cron.unschedule(jobid)
from cron.job
where jobname = 'rtc-media-orphan-cleanup';

select cron.schedule(
  'rtc-media-orphan-cleanup',
  '*/15 * * * *',
  $cron$
    select net.http_post(
      url := (select decrypted_secret from vault.decrypted_secrets where name = 'rtc_alert_scheduler_project_url') || '/functions/v1/media-orphan-cleanup',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'apikey', (select decrypted_secret from vault.decrypted_secrets where name = 'rtc_alert_scheduler_publishable_key'),
        'x-rtc-alert-dispatch-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'rtc_alert_dispatch_secret')
      ),
      body := jsonb_build_object('source', 'supabase-cron', 'limit', 100)
    );
  $cron$
);

commit;
