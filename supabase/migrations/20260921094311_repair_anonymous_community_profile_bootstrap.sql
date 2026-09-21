begin;

-- The profile after-write trigger calls this helper. The anonymous branch previously
-- performed an unconditional conflicting UPDATE on `profiles`, which retriggered the
-- same helper until PostgreSQL exhausted its stack. Only write when the canonical
-- anonymous projection actually differs, making the bootstrap idempotent.
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
      id,
      display_name,
      handle,
      avatar_path,
      bio,
      guidelines_version,
      guidelines_accepted_at,
      staff_badge
    )
    values (
      p_user_id,
      'Anonymous',
      'anonymous',
      null,
      '',
      private.current_community_guidelines_version(),
      now(),
      false
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
    id,
    display_name,
    handle,
    avatar_path,
    bio,
    guidelines_version,
    staff_badge
  )
  values (
    p_user_id,
    left(coalesce(nullif(btrim(v_display_name), ''), 'Community member'), 80),
    'member_' || replace(left(p_user_id::text, 20), '-', ''),
    v_avatar_path,
    '',
    0,
    false
  )
  on conflict (id) do update
    set display_name = excluded.display_name,
        avatar_path = excluded.avatar_path,
        updated_at = now();
end;
$$;

commit;
