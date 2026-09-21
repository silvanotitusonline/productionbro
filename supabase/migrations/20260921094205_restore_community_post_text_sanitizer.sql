begin;

-- `create_community_post_draft` is SECURITY DEFINER with an empty search path and
-- explicitly calls this canonical helper. Its absence made every post creation fail
-- before a draft could be written, while the Android client retained its local
-- optimistic placeholder. Keep the transformation deliberately minimal: trim outer
-- whitespace and remove control characters without changing normal resident text.
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

commit;
