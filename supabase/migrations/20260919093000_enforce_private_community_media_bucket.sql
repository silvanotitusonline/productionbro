-- Resident Community media is served through signed URLs and must never be public.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'rtc-community-media',
  'rtc-community-media',
  false,
  20971520,
  array['image/jpeg','image/png','image/webp','video/mp4','video/webm']
)
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;
