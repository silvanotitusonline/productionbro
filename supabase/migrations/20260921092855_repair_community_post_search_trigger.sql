begin;

-- `community_posts.search_vector` is a stored column in production. Keep the existing
-- before-write trigger, but build it only from columns that exist on `community_posts`.
-- The previous untracked function read `new.author_name`, which does not exist and aborts
-- both anonymous and authenticated post creation/finalization.
create or replace function public.community_posts_search_trigger()
returns trigger
language plpgsql
set search_path = pg_catalog
as $$
begin
  new.search_vector := to_tsvector('english', coalesce(new.body, ''));
  return new;
end;
$$;

commit;
