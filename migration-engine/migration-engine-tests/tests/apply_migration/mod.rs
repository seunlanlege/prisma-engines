#![allow(non_snake_case)]

use migration_connector::*;
use migration_engine_tests::sql::*;

#[test_each_connector]
async fn single_watch_migrations_must_work(api: &TestApi) {
    let migration_persistence = api.migration_persistence();

    let steps = vec![
        create_model_step("Test"),
        create_field_step("Test", "id", "Int"),
        create_id_directive_step("Test", "id"),
    ];

    let db_schema_1 = api.apply_migration(steps.clone(), "watch-0001").await.sql_schema;
    let migrations = migration_persistence.load_all().await.unwrap();

    assert_eq!(migrations.len(), 1);
    assert_eq!(migrations.first().unwrap().name, "watch-0001");

    let custom_migration_id = "a-custom-migration-id";
    let db_schema_2 = api.apply_migration(steps, custom_migration_id).await.sql_schema;

    assert_eq!(db_schema_1, db_schema_2);

    let migrations = migration_persistence.load_all().await.unwrap();

    assert_eq!(migrations.len(), 2);
    assert_eq!(migrations[0].name, "watch-0001");
    assert_eq!(migrations[1].name, custom_migration_id);
    assert_eq!(migrations[1].status, MigrationStatus::MigrationSuccess);
    assert!(migrations[1].finished_at.is_some());
}

#[test_each_connector]
async fn multiple_watch_migrations_must_work(api: &TestApi) {
    let migration_persistence = api.migration_persistence();

    let steps1 = vec![
        create_model_step("Test"),
        create_field_step("Test", "id", "Int"),
        create_id_directive_step("Test", "id"),
    ];

    api.apply_migration(steps1.clone(), "watch-0001").await;
    let migrations = migration_persistence.load_all().await.unwrap();

    assert_eq!(migrations.len(), 1);
    assert_eq!(migrations[0].name, "watch-0001");

    let steps2 = vec![create_field_step("Test", "field", "String")];
    let db_schema_2 = api.apply_migration(steps2.clone(), "watch-0002").await.sql_schema;
    let migrations = migration_persistence.load_all().await.unwrap();

    assert_eq!(migrations.len(), 2);
    assert_eq!(migrations[0].name, "watch-0001");
    assert_eq!(migrations[1].name, "watch-0002");

    let custom_migration_id = "a-custom-migration-id";
    let mut final_steps = Vec::new();

    final_steps.append(&mut steps1.clone());
    final_steps.append(&mut steps2.clone());

    let final_db_schema = api.apply_migration(final_steps, custom_migration_id).await.sql_schema;

    assert_eq!(db_schema_2, final_db_schema);

    let migrations = migration_persistence.load_all().await.unwrap();

    assert_eq!(migrations.len(), 3);
    assert_eq!(migrations[0].name, "watch-0001");
    assert_eq!(migrations[1].name, "watch-0002");

    assert_eq!(migrations[2].name, custom_migration_id);
    assert_eq!(migrations[2].status, MigrationStatus::MigrationSuccess);
    assert!(migrations[2].finished_at.is_some());
}

#[test_each_connector]
async fn steps_equivalence_criteria_is_satisfied_when_leaving_watch_mode(api: &TestApi) {
    let migration_persistence = api.migration_persistence();

    let steps1 = vec![
        create_model_step("Test"),
        create_field_step("Test", "id", "Int"),
        create_id_directive_step("Test", "id"),
    ];

    let db_schema1 = api.apply_migration(steps1.clone(), "watch-0001").await.sql_schema;

    let steps2 = vec![create_field_step("Test", "field", "String")];
    api.apply_migration(steps2.clone(), "watch-0002").await;

    let steps3 = vec![delete_field_step("Test", "field")];
    api.apply_migration(steps3.clone(), "watch-0003").await;

    let custom_migration_id = "a-custom-migration-id";
    let mut final_steps = Vec::new();
    final_steps.append(&mut steps1.clone()); // steps2 and steps3 eliminate each other

    let final_db_schema = api.apply_migration(final_steps, custom_migration_id).await.sql_schema;
    assert_eq!(db_schema1, final_db_schema);
    let migrations = migration_persistence.load_all().await.unwrap();
    assert_eq!(migrations[0].name, "watch-0001");
    assert_eq!(migrations[1].name, "watch-0002");
    assert_eq!(migrations[2].name, "watch-0003");
    assert_eq!(migrations[3].name, custom_migration_id);
}

#[test_each_connector]
async fn must_handle_additional_steps_when_transitioning_out_of_watch_mode(api: &TestApi) {
    let migration_persistence = api.migration_persistence();

    let steps1 = vec![
        create_model_step("Test"),
        create_field_step("Test", "id", "Int"),
        create_id_directive_step("Test", "id"),
    ];

    api.apply_migration(steps1.clone(), "watch-0001").await;

    let steps2 = vec![create_field_step("Test", "field1", "String")];
    api.apply_migration(steps2.clone(), "watch-0002").await;

    let custom_migration_id = "a-custom-migration-id";
    let additional_steps = vec![create_field_step("Test", "field2", "String")];
    let mut final_steps = Vec::new();

    final_steps.append(&mut steps1.clone());
    final_steps.append(&mut steps2.clone());
    final_steps.append(&mut additional_steps.clone());

    let final_db_schema = api.apply_migration(final_steps, custom_migration_id).await.sql_schema;
    assert_eq!(final_db_schema.tables.len(), 1);
    let table = final_db_schema.table_bang("Test");
    assert_eq!(table.columns.len(), 3);
    table.column_bang("id");
    table.column_bang("field1");
    table.column_bang("field2");

    let migrations = migration_persistence.load_all().await.unwrap();
    assert_eq!(migrations[0].name, "watch-0001");
    assert_eq!(migrations[1].name, "watch-0002");
    assert_eq!(migrations[2].name, custom_migration_id);
}

#[test_each_connector]
async fn applying_an_already_applied_migration_must_return_an_error(api: &TestApi) -> TestResult {
    let steps = vec![
        create_model_step("Test"),
        create_field_step("Test", "id", "Int"),
        create_id_directive_step("Test", "id"),
    ];

    let migration_id = "duplicate-migration";

    let cmd = api
        .apply()
        .migration_id(Some(migration_id.to_owned()))
        .steps(Some(steps))
        .force(Some(true));

    cmd.clone().send().await?;

    assert_eq!(
        cmd.send()
            .await
            .map_err(|err| err.to_string())
            .unwrap_err(),
        "Error in command input: Invariant violation: the migration with id `duplicate-migration` has already been applied.",
    );

    Ok(())
}
