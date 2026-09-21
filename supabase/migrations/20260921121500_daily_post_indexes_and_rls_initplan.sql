-- Production-readiness: covering indexes + (select auth.uid()) RLS initplan.
-- Applied 2026-09-21 on eqwstpdjoineycrkhpht; replay on pbzzfzfgwzwdstvnwzqu before store release.

CREATE INDEX IF NOT EXISTS daily_post_comment_reports_reporter_id_idx
  ON public.daily_post_comment_reports (reporter_id);

CREATE INDEX IF NOT EXISTS daily_post_comment_reports_reviewed_by_idx
  ON public.daily_post_comment_reports (reviewed_by);

DROP POLICY IF EXISTS daily_posts_editor_insert ON public.daily_posts;
CREATE POLICY daily_posts_editor_insert
  ON public.daily_posts
  FOR INSERT
  TO authenticated
  WITH CHECK (
    daily_post_can_edit()
    AND (author_id = (SELECT auth.uid()))
    AND (state = 'DRAFT'::text)
  );

DROP POLICY IF EXISTS daily_post_comments_owner_read ON public.daily_post_comments;
CREATE POLICY daily_post_comments_owner_read
  ON public.daily_post_comments
  FOR SELECT
  TO authenticated
  USING (
    (author_id = (SELECT auth.uid()))
    OR daily_post_can_moderate()
  );

DROP POLICY IF EXISTS daily_post_preview_own_read ON public.daily_post_preview_receipts;
CREATE POLICY daily_post_preview_own_read
  ON public.daily_post_preview_receipts
  FOR SELECT
  TO authenticated
  USING (user_id = (SELECT auth.uid()));
