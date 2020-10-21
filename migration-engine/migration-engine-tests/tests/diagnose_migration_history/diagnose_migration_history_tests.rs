use crate::*;
use migration_core::commands::{DiagnoseMigrationHistoryOutput, DriftDiagnostic, HistoryDiagnostic};
use pretty_assertions::assert_eq;
use user_facing_errors::UserFacingError;

#[test_each_connector]
async fn diagnose_migrations_history_on_an_empty_database_without_migration_returns_nothing(
    api: &TestApi,
) -> TestResult {
    let directory = api.create_migrations_directory()?;
    let output = api.diagnose_migration_history(&directory).send().await?.into_output();

    assert!(output.is_empty());

    Ok(())
}

#[test_each_connector]
async fn diagnose_migrations_history_after_two_migrations_happy_path(api: &TestApi) -> TestResult {
    let directory = api.create_migrations_directory()?;

    let dm1 = r#"
        model Cat {
            id      Int @id
            name    String
        }
    "#;

    api.create_migration("initial", dm1, &directory).send().await?;

    let dm2 = r#"
        model Cat {
            id          Int @id
            name        String
            fluffiness  Float
        }
    "#;

    api.create_migration("second-migration", dm2, &directory).send().await?;

    api.apply_migrations(&directory)
        .send()
        .await?
        .assert_applied_migrations(&["initial", "second-migration"])?;

    let output = api.diagnose_migration_history(&directory).send().await?.into_output();

    assert!(output.is_empty());

    Ok(())
}

#[test_each_connector]
async fn diagnose_migration_history_detects_drift(api: &TestApi) -> TestResult {
    let directory = api.create_migrations_directory()?;

    let dm1 = r#"
        model Cat {
            id      Int @id
            name    String
        }
    "#;

    api.create_migration("initial", dm1, &directory).send().await?;

    api.apply_migrations(&directory)
        .send()
        .await?
        .assert_applied_migrations(&["initial"])?;

    let dm2 = r#"
        model Cat {
            id          Int @id
            name        String
            fluffiness  Float
        }
    "#;

    api.schema_push(dm2).send().await?;

    let DiagnoseMigrationHistoryOutput {
        drift,
        history,
        failed_migration_names,
        edited_migration_names,
    } = api.diagnose_migration_history(&directory).send().await?.into_output();

    assert_eq!(drift, Some(DriftDiagnostic::DriftDetected));
    assert!(history.is_none());
    assert!(failed_migration_names.is_empty());
    assert!(edited_migration_names.is_empty());

    Ok(())
}

#[test_each_connector]
async fn diagnose_migrations_history_can_detect_when_the_database_is_behind(api: &TestApi) -> TestResult {
    let directory = api.create_migrations_directory()?;

    let dm1 = r#"
        model Cat {
            id      Int @id
            name    String
        }
    "#;

    api.create_migration("initial", dm1, &directory).send().await?;

    api.apply_migrations(&directory)
        .send()
        .await?
        .assert_applied_migrations(&["initial"])?;

    let dm2 = r#"
        model Cat {
            id          Int @id
            name        String
            fluffiness  Float
        }
    "#;

    let name = api
        .create_migration("second-migration", dm2, &directory)
        .send()
        .await?
        .into_output()
        .generated_migration_name
        .unwrap();

    let DiagnoseMigrationHistoryOutput {
        drift,
        history,
        failed_migration_names,
        edited_migration_names,
    } = api.diagnose_migration_history(&directory).send().await?.into_output();

    assert!(drift.is_none());
    assert!(failed_migration_names.is_empty());
    assert!(edited_migration_names.is_empty());
    assert_eq!(
        history,
        Some(HistoryDiagnostic::DatabaseIsBehind {
            unapplied_migration_names: vec![name],
        })
    );

    Ok(())
}

#[test_each_connector]
async fn diagnose_migrations_history_can_detect_when_the_folder_is_behind(api: &TestApi) -> TestResult {
    let directory = api.create_migrations_directory()?;

    let dm1 = r#"
        model Cat {
            id      Int @id
            name    String
        }
    "#;

    api.create_migration("initial", dm1, &directory).send().await?;

    let dm2 = r#"
        model Cat {
            id          Int @id
            name        String
            fluffiness  Float
        }
    "#;

    let name = api
        .create_migration("second-migration", dm2, &directory)
        .send()
        .await?
        .into_output()
        .generated_migration_name
        .unwrap();

    api.apply_migrations(&directory)
        .send()
        .await?
        .assert_applied_migrations(&["initial", "second-migration"])?;

    let second_migration_folder_path = directory.path().join(&name);
    std::fs::remove_dir_all(&second_migration_folder_path)?;

    let DiagnoseMigrationHistoryOutput {
        drift,
        history,
        failed_migration_names,
        edited_migration_names,
    } = api.diagnose_migration_history(&directory).send().await?.into_output();

    assert!(failed_migration_names.is_empty());
    assert!(edited_migration_names.is_empty());
    assert_eq!(drift, Some(DriftDiagnostic::DriftDetected));
    assert_eq!(
        history,
        Some(HistoryDiagnostic::MigrationsDirectoryIsBehind {
            unpersisted_migration_names: vec![name],
        })
    );

    Ok(())
}

#[test_each_connector]
async fn diagnose_migrations_history_can_detect_when_history_diverges(api: &TestApi) -> TestResult {
    let directory = api.create_migrations_directory()?;

    let dm1 = r#"
        model Cat {
            id      Int @id
            name    String
        }
    "#;

    let first_migration_name = api
        .create_migration("1-initial", dm1, &directory)
        .send()
        .await?
        .into_output()
        .generated_migration_name
        .unwrap();

    let dm2 = r#"
        model Cat {
            id          Int @id
            name        String
            fluffiness  Float
        }
    "#;

    let deleted_migration_name = api
        .create_migration("2-second-migration", dm2, &directory)
        .send()
        .await?
        .into_output()
        .generated_migration_name
        .unwrap();

    api.apply_migrations(&directory)
        .send()
        .await?
        .assert_applied_migrations(&["1-initial", "2-second-migration"])?;

    let second_migration_folder_path = directory.path().join(&deleted_migration_name);
    std::fs::remove_dir_all(&second_migration_folder_path)?;

    let dm3 = r#"
        model Dog {
            id          Int @id
            name        String
            fluffiness  Float
        }
    "#;

    let unapplied_migration_name = api
        .create_migration("3-create-dog", dm3, &directory)
        .draft(true)
        .send()
        .await?
        .assert_migration_directories_count(2)?
        .into_output()
        .generated_migration_name
        .unwrap();

    let DiagnoseMigrationHistoryOutput {
        history,
        drift,
        failed_migration_names,
        edited_migration_names,
    } = api.diagnose_migration_history(&directory).send().await?.into_output();

    assert!(failed_migration_names.is_empty());
    assert!(edited_migration_names.is_empty());
    assert_eq!(drift, Some(DriftDiagnostic::DriftDetected));
    assert_eq!(
        history,
        Some(HistoryDiagnostic::HistoriesDiverge {
            unapplied_migration_names: vec![unapplied_migration_name],
            unpersisted_migration_names: vec![deleted_migration_name],
            last_common_migration_name: Some(first_migration_name),
        })
    );

    Ok(())
}

#[test_each_connector]
async fn diagnose_migrations_history_can_detect_edited_migrations(api: &TestApi) -> TestResult {
    let directory = api.create_migrations_directory()?;

    let dm1 = r#"
        model Cat {
            id      Int @id
            name    String
        }
    "#;

    let initial_assertions = api.create_migration("initial", dm1, &directory).send().await?;

    let dm2 = r#"
        model Cat {
            id          Int @id
            name        String
            fluffiness  Float
        }
    "#;

    api.create_migration("second-migration", dm2, &directory).send().await?;

    api.apply_migrations(&directory)
        .send()
        .await?
        .assert_applied_migrations(&["initial", "second-migration"])?;

    let initial_migration_name = initial_assertions
        .modify_migration(|script| {
            std::mem::swap(script, &mut format!("/* test */\n{}", script));
        })?
        .into_output()
        .generated_migration_name
        .unwrap();

    let DiagnoseMigrationHistoryOutput {
        drift,
        history,
        edited_migration_names,
        failed_migration_names,
    } = api.diagnose_migration_history(&directory).send().await?.into_output();

    assert!(drift.is_none());
    assert!(history.is_none());
    assert!(failed_migration_names.is_empty());
    assert_eq!(edited_migration_names, &[initial_migration_name]);

    Ok(())
}

#[test_each_connector]
async fn diagnose_migrations_history_reports_migrations_failing_to_apply_cleanly(api: &TestApi) -> TestResult {
    let directory = api.create_migrations_directory()?;

    let dm1 = r#"
        model Cat {
            id      Int @id
            name    String
        }
    "#;

    let initial_assertions = api.create_migration("initial", dm1, &directory).send().await?;

    let dm2 = r#"
        model Cat {
            id          Int @id
            name        String
            fluffiness  Float
        }
    "#;

    api.create_migration("second-migration", dm2, &directory).send().await?;

    api.apply_migrations(&directory)
        .send()
        .await?
        .assert_applied_migrations(&["initial", "second-migration"])?;

    let _initial_migration_name = initial_assertions
        .modify_migration(|script| {
            script.push_str("SELECT YOLO;\n");
        })?
        .into_output()
        .generated_migration_name
        .unwrap();

    let known_error = api
        .diagnose_migration_history(&directory)
        .send()
        .await
        .unwrap_err()
        .render_user_facing()
        .unwrap_known();

    assert_eq!(
        known_error.error_code,
        user_facing_errors::migration_engine::MigrationDoesNotApplyCleanly::ERROR_CODE
    );

    // let DiagnoseMigrationHistoryOutput {
    //     failed_migration_names,
    //     edited_migration_names,
    //     history,
    //     drift,
    // } = api.diagnose_migration_history(&directory).send().await?.into_output();

    // assert_eq!(edited_migration_names, &[initial_migration_name.as_str()]);
    // assert!(failed_migration_names.is_empty());
    // assert_eq!(history, None);

    // match &drift {
    //     Some(DriftDiagnostic::MigrationFailedToApply { error, migration_name }) => {
    //         assert_eq!(migration_name.as_str(), initial_migration_name.as_str());
    //         assert!(error.contains("yolo") || error.contains("YOLO"))
    //     }
    //     _ => panic!("assertion failed"),
    // }

    Ok(())
}

#[test_each_connector]
async fn diagnose_migrations_history_with_a_nonexistent_migrations_directory_works(api: &TestApi) -> TestResult {
    let directory = api.create_migrations_directory()?;

    std::fs::remove_dir(directory.path())?;

    let DiagnoseMigrationHistoryOutput {
        drift,
        history,
        edited_migration_names,
        failed_migration_names,
    } = api.diagnose_migration_history(&directory).send().await?.into_output();

    assert!(drift.is_none());
    assert!(history.is_none());
    assert!(failed_migration_names.is_empty());
    assert!(edited_migration_names.is_empty());

    Ok(())
}
