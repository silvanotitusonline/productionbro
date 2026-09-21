-- Guest mode permissions for Supabase anonymous-authenticated sessions.
-- Anonymous users may create community content and civic reports, but may not
-- mutate profiles or account preferences.

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

revoke all on function private.is_anonymous_guest() from public, anon;
grant execute on function private.is_anonymous_guest() to authenticated;

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
  if p_user_id is null then return; end if;

  if p_user_id = auth.uid() and private.is_anonymous_guest() then
    insert into public.profiles (id, display_name)
    values (p_user_id, 'Anonymous')
    on conflict (id) do update set display_name = 'Anonymous', avatar_url = null;

    insert into public.community_profiles (
      id, display_name, handle, avatar_path, bio,
      guidelines_version, guidelines_accepted_at, staff_badge
    ) values (
      p_user_id, 'Anonymous', 'anonymous', null, '',
      private.current_community_guidelines_version(), now(), false
    )
    on conflict (id) do update set
      display_name = 'Anonymous',
      handle = 'anonymous',
      avatar_path = null,
      updated_at = now();
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
  if not found then return; end if;

  insert into public.community_profiles (
    id, display_name, handle, avatar_path, bio, guidelines_version, staff_badge
  ) values (
    p_user_id,
    left(coalesce(nullif(btrim(v_display_name), ''), 'Community member'), 80),
    'member_' || replace(left(p_user_id::text, 12), '-', ''),
    v_avatar_path,
    '',
    0,
    false
  )
  on conflict (id) do update set
    display_name = excluded.display_name,
    avatar_path = excluded.avatar_path,
    updated_at = now();
end;
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

-- Explicit guest insert policy. The existing authenticated policy remains in
-- place for registered users; this policy documents and enforces the guest
-- author identity contract independently.
create policy community_comments_insert_anonymous_guest
  on public.community_comments
  as permissive
  for insert
  to authenticated
  with check (
    private.is_anonymous_guest()
    and author_id = auth.uid()
    and parent_comment_id is null
    and state = 'PUBLISHED'
    and report_count = 0
    and deleted_at is null
    and exists (
      select 1
        from public.community_posts p
       where p.id = community_comments.post_id
         and p.state = 'PUBLISHED'
         and p.deleted_at is null
         and p.is_locked = false
         and private.can_view_community_author(p.author_id)
    )
  );

-- Anonymous users use the existing authenticated-only post RPCs. Their
-- profile is created by ensure_community_profile_for_account above, and the
-- RPCs require auth.uid(), so the database never accepts a caller-supplied
-- author identity.
grant execute on function public.create_community_post_draft(text, uuid) to authenticated;
grant execute on function public.publish_community_post(uuid) to authenticated;
grant execute on function public.create_community_comment(uuid, text, uuid) to authenticated;

-- Public reports are RPC-only. The resident guard accepts anonymous-auth users
-- after the community profile/guideline exception above, while direct table
-- access remains denied.
grant execute on function public.civic_report_create_v1(
  uuid, text, text, timestamptz, uuid, text, text, text, text,
  double precision, double precision, text, text, boolean, text
) to authenticated;

create policy profiles_anonymous_guest_update_denied
  on public.profiles
  as restrictive
  for update
  to authenticated
  using (not private.is_anonymous_guest())
  with check (not private.is_anonymous_guest());

create policy account_preferences_anonymous_guest_update_denied
  on public.account_preferences
  as restrictive
  for update
  to authenticated
  using (not private.is_anonymous_guest())
  with check (not private.is_anonymous_guest());
