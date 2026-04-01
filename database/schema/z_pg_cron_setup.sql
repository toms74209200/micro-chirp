CREATE EXTENSION IF NOT EXISTS pg_cron ;

SELECT cron.schedule (
'refresh-posts-mv',
'* * * * *',
$$
    REFRESH MATERIALIZED VIEW CONCURRENTLY posts_mv;
    UPDATE mv_refresh_log SET last_refreshed_at = CURRENT_TIMESTAMP WHERE view_name = 'posts_mv';
    $$
) ;
