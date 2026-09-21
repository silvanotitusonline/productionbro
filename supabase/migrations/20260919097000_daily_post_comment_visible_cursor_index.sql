begin;

create index if not exists daily_post_comments_visible_cursor_idx
  on public.daily_post_comments(post_id, created_at desc, id desc)
  where state = 'VISIBLE';

commit;
