use crate::{connection_wrapper::Connection, SqlMigrationConnector};
use barrel::types;
use chrono::*;
use migration_connector::*;
use quaint::{ast::*, connector::ResultSet, prelude::SqlFamily};
use std::convert::TryFrom;

#[async_trait::async_trait]
impl MigrationPersistence for SqlMigrationConnector {
    async fn init(&self) -> Result<(), ConnectorError> {
        let sql_str = match self.sql_family() {
            SqlFamily::Sqlite => {
                let mut m = barrel::Migration::new().schema(self.schema_name());
                m.create_table_if_not_exists(MIGRATION_TABLE_NAME, migration_table_setup_sqlite);
                m.make_from(barrel::SqlVariant::Sqlite)
            }
            SqlFamily::Postgres => {
                let mut m = barrel::Migration::new().schema(self.schema_name());
                m.create_table(MIGRATION_TABLE_NAME, migration_table_setup_postgres);
                m.schema(self.schema_name()).make_from(barrel::SqlVariant::Pg)
            }
            SqlFamily::Mysql => {
                let mut m = barrel::Migration::new().schema(self.schema_name());
                m.create_table(MIGRATION_TABLE_NAME, migration_table_setup_mysql);
                m.make_from(barrel::SqlVariant::Mysql)
            }
            SqlFamily::Mssql => {
                let mut m = barrel::Migration::new().schema(self.schema_name());
                m.create_table_if_not_exists(MIGRATION_TABLE_NAME, migration_table_setup_mssql);
                m.make_from(barrel::SqlVariant::Mssql)
            }
        };

        self.conn().raw_cmd(&sql_str).await.ok();

        Ok(())
    }

    async fn reset(&self) -> ConnectorResult<()> {
        use quaint::ast::Delete;

        self.conn()
            .query(Delete::from_table((self.schema_name(), MIGRATION_TABLE_NAME)))
            .await
            .ok();

        Ok(())
    }

    async fn last_two_migrations(&self) -> ConnectorResult<(Option<Migration>, Option<Migration>)> {
        last_applied_migrations(self.conn(), self.table()).await
    }

    async fn load_all(&self) -> ConnectorResult<Vec<Migration>> {
        let query = Select::from_table(self.table()).order_by(REVISION_COLUMN.ascend());
        let result_set = self.conn().query(query).await?;

        Ok(parse_rows_new(result_set))
    }

    async fn by_name(&self, name: &str) -> ConnectorResult<Option<Migration>> {
        let conditions = NAME_COLUMN.equals(name);
        let query = Select::from_table(self.table())
            .so_that(conditions)
            .order_by(REVISION_COLUMN.descend());
        let result_set = self.conn().query(query).await?;

        Ok(parse_rows_new(result_set).into_iter().next())
    }

    async fn create(&self, migration: Migration) -> Result<Migration, ConnectorError> {
        let mut cloned = migration.clone();
        let model_steps_json = serde_json::to_string(&migration.datamodel_steps).unwrap();
        let database_migration_json = serde_json::to_string(&migration.database_migration).unwrap();
        let errors_json = serde_json::to_string(&migration.errors).unwrap();

        let insert = Insert::single_into(self.table())
            .value(DATAMODEL_COLUMN, migration.datamodel_string)
            .value(NAME_COLUMN, migration.name)
            .value(STATUS_COLUMN, migration.status.code())
            .value(APPLIED_COLUMN, migration.applied)
            .value(ROLLED_BACK_COLUMN, migration.rolled_back)
            .value(DATAMODEL_STEPS_COLUMN, model_steps_json)
            .value(DATABASE_MIGRATION_COLUMN, database_migration_json)
            .value(ERRORS_COLUMN, errors_json)
            .value(STARTED_AT_COLUMN, self.convert_datetime(migration.started_at))
            .value(FINISHED_AT_COLUMN, Option::<DateTime<Utc>>::None);

        match self.sql_family() {
            SqlFamily::Sqlite | SqlFamily::Mysql => {
                let result_set = self.conn().query(insert).await.unwrap();
                let id = result_set.last_insert_id().unwrap();

                cloned.revision = usize::try_from(id).unwrap();
            }
            SqlFamily::Postgres | SqlFamily::Mssql => {
                let returning_insert = Insert::from(insert).returning(&["revision"]);
                let result_set = self.conn().query(returning_insert).await.unwrap();

                if let Some(row) = result_set.into_iter().next() {
                    cloned.revision = row["revision"].as_i64().unwrap() as usize;
                }
            }
        }

        Ok(cloned)
    }

    async fn update(&self, params: &MigrationUpdateParams) -> Result<(), ConnectorError> {
        let finished_at_value = match params.finished_at {
            Some(x) => self.convert_datetime(x),
            None => Value::from(Option::<DateTime<Utc>>::None),
        };
        let errors_json = serde_json::to_string(&params.errors).unwrap();
        let query = Update::table(self.table())
            .set(NAME_COLUMN, params.new_name.clone())
            .set(STATUS_COLUMN, params.status.code())
            .set(APPLIED_COLUMN, params.applied)
            .set(ROLLED_BACK_COLUMN, params.rolled_back)
            .set(ERRORS_COLUMN, errors_json)
            .set(FINISHED_AT_COLUMN, finished_at_value)
            .so_that(
                NAME_COLUMN
                    .equals(params.name.clone())
                    .and(REVISION_COLUMN.equals(params.revision)),
            );

        self.conn().query(query).await?;

        Ok(())
    }
}

/// Returns the last 2 applied migrations, or a shorter vec in absence of applied migrations.
async fn last_applied_migrations(
    conn: &Connection,
    table: Table<'_>,
) -> ConnectorResult<(Option<Migration>, Option<Migration>)> {
    let conditions = STATUS_COLUMN.equals(MigrationStatus::MigrationSuccess.code());
    let query = Select::from_table(table)
        .so_that(conditions)
        .order_by(REVISION_COLUMN.descend())
        .limit(2);

    let result_set = conn.query(query).await?;
    let mut rows = parse_rows_new(result_set).into_iter();
    let last = rows.next();
    let second_to_last = rows.next();
    Ok((last, second_to_last))
}

fn migration_table_setup_sqlite(t: &mut barrel::Table) {
    migration_table_setup(t, types::text(), types::custom("DATETIME"), types::custom("TEXT"));
}

fn migration_table_setup_postgres(t: &mut barrel::Table) {
    migration_table_setup(t, types::text(), types::custom("timestamp(3)"), types::custom("TEXT"));
}

fn migration_table_setup_mysql(t: &mut barrel::Table) {
    migration_table_setup(
        t,
        types::text(),
        types::custom("datetime(3)"),
        types::custom("LONGTEXT"),
    );
}

fn migration_table_setup_mssql(t: &mut barrel::Table) {
    migration_table_setup(
        t,
        types::custom("nvarchar(max)"),
        types::custom("datetime2"),
        types::custom("nvarchar(max)"),
    );
}

fn migration_table_setup(
    t: &mut barrel::Table,
    text_type: barrel::types::Type,
    datetime_type: barrel::types::Type,
    unlimited_text_type: barrel::types::Type,
) {
    t.add_column(REVISION_COLUMN, types::primary());
    t.add_column(NAME_COLUMN, text_type.clone());
    t.add_column(DATAMODEL_COLUMN, unlimited_text_type.clone());
    t.add_column(STATUS_COLUMN, text_type);
    t.add_column(APPLIED_COLUMN, types::integer());
    t.add_column(ROLLED_BACK_COLUMN, types::integer());
    t.add_column(DATAMODEL_STEPS_COLUMN, unlimited_text_type.clone());
    t.add_column(DATABASE_MIGRATION_COLUMN, unlimited_text_type.clone());
    t.add_column(ERRORS_COLUMN, unlimited_text_type);
    t.add_column(STARTED_AT_COLUMN, datetime_type.clone());
    t.add_column(FINISHED_AT_COLUMN, datetime_type.nullable(true));
}

impl SqlMigrationConnector {
    fn table(&self) -> Table<'_> {
        match self.sql_family() {
            SqlFamily::Sqlite => {
                // sqlite case. Otherwise quaint produces invalid SQL
                MIGRATION_TABLE_NAME.to_string().into()
            }
            _ => (self.schema_name().to_string(), MIGRATION_TABLE_NAME.to_string()).into(),
        }
    }

    fn convert_datetime(&self, datetime: DateTime<Utc>) -> Value<'_> {
        match self.sql_family() {
            SqlFamily::Sqlite => Value::integer(datetime.timestamp_millis()),
            SqlFamily::Postgres => Value::datetime(datetime),
            SqlFamily::Mysql => Value::datetime(datetime),
            SqlFamily::Mssql => Value::datetime(datetime),
        }
    }
}

fn convert_parameterized_date_value(db_value: &Value<'_>) -> DateTime<Utc> {
    match db_value {
        Value::Integer(Some(x)) => timestamp_to_datetime(*x),
        Value::DateTime(Some(x)) => *x,
        Value::Date(Some(date)) => DateTime::from_utc(date.and_hms(0, 0, 0), Utc),
        x => unimplemented!("Got unsupported value {:?} in date conversion", x),
    }
}

fn timestamp_to_datetime(timestamp: i64) -> DateTime<Utc> {
    let nsecs = ((timestamp % 1000) * 1_000_000) as u32;
    let secs = (timestamp / 1000) as i64;
    let naive = chrono::NaiveDateTime::from_timestamp(secs, nsecs);
    let datetime: DateTime<Utc> = DateTime::from_utc(naive, Utc);

    datetime
}

fn parse_rows_new(result_set: ResultSet) -> Vec<Migration> {
    result_set
        .into_iter()
        .map(|row| {
            let datamodel_string: String = row[DATAMODEL_COLUMN].to_string().unwrap();
            let datamodel_steps_json: String = row[DATAMODEL_STEPS_COLUMN].to_string().unwrap();

            let database_migration_string: String = row[DATABASE_MIGRATION_COLUMN].to_string().unwrap();
            let errors_json: String = row[ERRORS_COLUMN].to_string().unwrap();

            let finished_at = match &row[FINISHED_AT_COLUMN] {
                v if v.is_null() => None,
                x => Some(convert_parameterized_date_value(x)),
            };

            let datamodel_steps =
                serde_json::from_str(&datamodel_steps_json).expect("Error parsing the migration steps");

            let database_migration_json =
                serde_json::from_str(&database_migration_string).expect("Error parsing the database migration steps");
            let errors: Vec<String> = serde_json::from_str(&errors_json).unwrap();

            Migration {
                name: row[NAME_COLUMN].to_string().unwrap(),
                revision: row[REVISION_COLUMN].as_i64().unwrap() as usize,
                datamodel_string,
                status: row[STATUS_COLUMN].to_string().unwrap().parse().unwrap(),
                applied: row[APPLIED_COLUMN].as_i64().unwrap() as usize,
                rolled_back: row[ROLLED_BACK_COLUMN].as_i64().unwrap() as usize,
                datamodel_steps,
                database_migration: database_migration_json,
                errors,
                started_at: convert_parameterized_date_value(&row[STARTED_AT_COLUMN]),
                finished_at,
            }
        })
        .collect()
}

/// The name of the migrations table.
pub static MIGRATION_TABLE_NAME: &str = "_Migration";
static NAME_COLUMN: &str = "name";
static REVISION_COLUMN: &str = "revision";
static DATAMODEL_COLUMN: &str = "datamodel";
static STATUS_COLUMN: &str = "status";
static APPLIED_COLUMN: &str = "applied";
static ROLLED_BACK_COLUMN: &str = "rolled_back";
static DATAMODEL_STEPS_COLUMN: &str = "datamodel_steps";
static DATABASE_MIGRATION_COLUMN: &str = "database_migration";
static ERRORS_COLUMN: &str = "errors";
static STARTED_AT_COLUMN: &str = "started_at";
static FINISHED_AT_COLUMN: &str = "finished_at";
