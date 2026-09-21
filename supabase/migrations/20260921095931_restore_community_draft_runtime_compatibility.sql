begin;

-- Keep the runtime contract shared by the currently distributed Android debug APK
-- and the production schema. The client sends a UUID idempotency key to the draft
-- RPC; older non-production schemas exposed only the one-argument overload.
alter table public.community_posts
  add column if not exists client_post_id uuid;

create unique index if not exists community_posts_author_client_post_id_uq
  on public.community_posts(author_id, client_post_id)
  where client_post_id is not null;

create or replace function public.sanitize_input_text(p_value text)
returns text
language sql
immutable
set search_path = pg_catalog
as $$
  select btrim(regexp_replace(coalesce(p_value, ''), '[[:cntrl:]]', '', 'g'));
$$;

revoke all on function public.sanitize_input_text(text) from public, anon;
grant execute on function public.sanitize_input_text(text) to authenticated;

create or replace function private.is_anonymous_guest()
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select auth.uid() is not null
    and coalesce(auth.jwt() ->> 'is_anonymous', 'false') = 'true';
$$;

create or replace function private.community_guidelines_accepted()
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select private.is_anonymous_guest()
    or exists (
      select 1
      from public.community_profiles cp
      where cp.id = auth.uid()
        and cp.guidelines_version >= private.current_community_guidelines_version()
        and cp.guidelines_accepted_at is not null
    );
$$;

-- The profile after-write trigger invokes this helper. Make the anonymous branch
-- idempotent so updating its canonical values cannot recurse through that trigger.
create or replace function private.ensure_community_profile_for_account(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_display_name text;
  v_avatar_path text;
begin
  if p_user_id is null then
    return;
  end if;

  if p_user_id = auth.uid() and private.is_anonymous_guest() then
    insert into public.profiles (id, display_name)
    values (p_user_id, 'Anonymous')
    on conflict (id) do update
      set display_name = excluded.display_name,
          avatar_url = null
      where public.profiles.display_name is distinct from excluded.display_name
         or public.profiles.avatar_url is not null;

    insert into public.community_profiles (
      id, display_name, handle, avatar_path, bio,
      guidelines_version, guidelines_accepted_at, staff_badge
    )
    values (
      p_user_id, 'Anonymous', 'anonymous', null, '',
      private.current_community_guidelines_version(), now(), false
    )
    on conflict (id) do update
      set display_name = excluded.display_name,
          handle = excluded.handle,
          avatar_path = null,
          updated_at = now()
      where public.community_profiles.display_name is distinct from excluded.display_name
         or public.community_profiles.handle is distinct from excluded.handle
         or public.community_profiles.avatar_path is not null;
    return;
  end if;

  if not exists (select 1 from auth.users u where u.id = p_user_id) then
    raise exception 'AUTH_ACCOUNT_NOT_FOUND';
  end if;

  insert into public.profiles (id, display_name)
  values (p_user_id, 'Community member')
  on conflict (id) do nothing;

  select p.display_name, p.avatar_url
    into v_display_name, v_avatar_path
  from public.profiles p
  where p.id = p_user_id;

  if not found then
    raise exception 'PROFILE_NOT_READY';
  end if;

  insert into public.community_profiles (
    id, display_name, handle, avatar_path, bio, guidelines_version, staff_badge
  )
  values (
    p_user_id,
    left(coalesce(nullif(btrim(v_display_name), ''), 'Community member'), 80),
    'member_' || replace(left(p_user_id::text, 20), '-', ''),
    v_avatar_path, '', 0, false
  )
  on conflict (id) do update
    set display_name = excluded.display_name,
        avatar_path = excluded.avatar_path,
        updated_at = now();
end;
$$;

create or replace function public.create_community_post_draft(
  p_body text default '',
  p_client_post_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_post_id uuid;
  v_sanitized text;
begin
  if v_user_id is null then
    raise exception 'AUTH_REQUIRED';
  end if;

  v_sanitized := public.sanitize_input_text(p_body);
  if char_length(v_sanitized) > 280 then
    raise exception 'INVALID_POST';
  end if;

  perform private.ensure_community_profile_for_account(v_user_id);
  if not private.community_guidelines_accepted() then
    raise exception 'GUIDELINES_NOT_ACCEPTED';
  end if;

  if p_client_post_id is not null then
    select id
      into v_post_id
    from public.community_posts
    where author_id = v_user_id
      and client_post_id = p_client_post_id
      and deleted_at is null
    limit 1;

    if v_post_id is not null then
      return v_post_id;
    end if;
  end if;

  insert into public.community_posts (
    author_id, body, client_post_id, state, is_locked, report_count
  )
  values (
    v_user_id, v_sanitized, p_client_post_id, 'DRAFT', false, 0
  )
  returning id into v_post_id;

  return v_post_id;
end;
$$;

drop function if exists public.create_community_post_draft(text);

revoke all on function public.create_community_post_draft(text, uuid) from public, anon;
grant execute on function public.create_community_post_draft(text, uuid) to authenticated, service_role;

commit;
