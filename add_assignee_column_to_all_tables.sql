CREATE OR REPLACE FUNCTION add_assignee_column_to_all_tables()
RETURNS VOID
AS $$
DECLARE
 my_row RECORD;
BEGIN
 FOR my_row IN
 SELECT table_name, table_schema
 FROM information_schema.tables
 WHERE table_schema = 'aggregate'
 LOOP
 IF NOT EXISTS
 (
 SELECT attname FROM pg_attribute WHERE attrelid =
 (SELECT oid FROM pg_class WHERE relname = my_row.table_name )
 AND attname = '_ASSIGNEE_USERNAME'
 )
 THEN
 EXECUTE('ALTER TABLE ' || my_row.table_schema || '."' || my_row.table_name || '" ADD COLUMN "_ASSIGNEE_USERNAME" character varying(80) NULL;');
 END IF;
 END LOOP;
END
$$
LANGUAGE plpgsql;