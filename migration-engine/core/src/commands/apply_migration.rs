use super::MigrationStepsResultOutput;
use crate::{commands::command::*, CoreError};
use crate::{migration_engine::MigrationEngine, CoreResult};
use datamodel::{ast::SchemaAst, Datamodel};
use migration_connector::*;
use serde::Deserialize;

pub struct ApplyMigrationCommand<'a> {
    input: &'a ApplyMigrationInput,
}

#[async_trait::async_trait]
impl<'a> MigrationCommand for ApplyMigrationCommand<'a> {
    type Input = ApplyMigrationInput;
    type Output = MigrationStepsResultOutput;

    async fn execute<C, D>(input: &Self::Input, engine: &MigrationEngine<C, D>) -> CoreResult<Self::Output>
    where
        C: MigrationConnector<DatabaseMigration = D>,
        D: DatabaseMigrationMarker + Send + Sync + 'static,
    {
        let cmd = ApplyMigrationCommand { input };
        tracing::debug!("{:?}", cmd.input);

        let connector = engine.connector();
        let migration_persistence = connector.migration_persistence();

        match migration_persistence.last().await? {
            Some(ref last_migration) if last_migration.is_watch_migration() && !cmd.input.is_watch_migration() => {
                cmd.handle_transition_out_of_watch_mode(&engine).await
            }
            _ => cmd.handle_normal_migration(&engine).await,
        }
    }
}

impl<'a> ApplyMigrationCommand<'a> {
    async fn handle_transition_out_of_watch_mode<C, D>(
        &self,
        engine: &MigrationEngine<C, D>,
    ) -> CoreResult<MigrationStepsResultOutput>
    where
        C: MigrationConnector<DatabaseMigration = D>,
        D: DatabaseMigrationMarker + Send + Sync + 'static,
    {
        let connector = engine.connector();

        let migration_persistence = connector.migration_persistence();

        let last_migration = migration_persistence.last().await?;
        let current_datamodel = last_migration
            .map(|migration| migration.parse_datamodel())
            .unwrap_or_else(|| Ok(Datamodel::new()))
            .map_err(CoreError::InvalidPersistedDatamodel)?;

        let last_non_watch_datamodel = migration_persistence
            .last_non_watch_migration()
            .await?
            .map(|m| m.parse_schema_ast())
            .unwrap_or_else(|| Ok(SchemaAst::empty()))
            .map_err(CoreError::InvalidPersistedDatamodel)?;
        let next_datamodel_ast = engine
            .datamodel_calculator()
            .infer(&last_non_watch_datamodel, self.input.steps.as_slice())?;

        self.handle_migration(&engine, current_datamodel, next_datamodel_ast)
            .await
    }

    async fn handle_normal_migration<C, D>(
        &self,
        engine: &MigrationEngine<C, D>,
    ) -> CoreResult<MigrationStepsResultOutput>
    where
        C: MigrationConnector<DatabaseMigration = D>,
        D: DatabaseMigrationMarker + Send + Sync + 'static,
    {
        let connector = engine.connector();
        let migration_persistence = connector.migration_persistence();

        if migration_persistence
            .migration_is_already_applied(&self.input.migration_id)
            .await?
        {
            return Err(CoreError::Input(anyhow::anyhow!(
                "Invariant violation: the migration with id `{migration_id}` has already been applied.",
                migration_id = self.input.migration_id
            )));
        }

        let last_migration = migration_persistence.last().await?;
        let current_datamodel_ast = last_migration
            .as_ref()
            .map(|migration| migration.parse_schema_ast())
            .unwrap_or_else(|| Ok(SchemaAst::empty()))
            .map_err(CoreError::InvalidPersistedDatamodel)?;
        let current_datamodel = last_migration
            .map(|migration| migration.parse_datamodel())
            .unwrap_or_else(|| Ok(Datamodel::new()))
            .map_err(CoreError::InvalidPersistedDatamodel)?;

        let next_datamodel_ast = engine
            .datamodel_calculator()
            .infer(&current_datamodel_ast, self.input.steps.as_slice())?;

        self.handle_migration(&engine, current_datamodel, next_datamodel_ast)
            .await
    }

    async fn handle_migration<C, D>(
        &self,
        engine: &MigrationEngine<C, D>,
        current_datamodel: Datamodel,
        next_schema_ast: SchemaAst,
    ) -> CoreResult<MigrationStepsResultOutput>
    where
        C: MigrationConnector<DatabaseMigration = D>,
        D: DatabaseMigrationMarker + Send + Sync + 'static,
    {
        let connector = engine.connector();
        let next_datamodel =
            datamodel::lift_ast_to_datamodel(&next_schema_ast).map_err(CoreError::ProducedBadDatamodel)?;
        let migration_persistence = connector.migration_persistence();

        let database_migration = connector
            .database_migration_inferrer()
            .infer(&current_datamodel, &next_datamodel.subject, &self.input.steps)
            .await?;

        let database_steps_json_pretty = connector
            .database_migration_step_applier()
            .render_steps_pretty(&database_migration)?;

        tracing::trace!(?database_steps_json_pretty);

        let database_migration_json = database_migration.serialize();

        let migration = Migration::new(NewMigration {
            name: self.input.migration_id.clone(),
            datamodel_steps: self.input.steps.clone(),
            datamodel_string: datamodel::render_schema_ast_to_string(&next_schema_ast)
                .map_err(CoreError::ProducedBadDatamodel)?,
            database_migration: database_migration_json,
        });

        let diagnostics = connector
            .destructive_change_checker()
            .check(&database_migration)
            .await?;

        match (
            !diagnostics.unexecutable_migrations.is_empty(),
            diagnostics.has_warnings(),
            self.input.force.unwrap_or(false),
        ) {
            (true, _, _) => {
                tracing::info!("There are unexecutable migration steps, the migration will not be applied.")
            }
            // We have no warnings, or the force flag is passed.
            (_, false, _) | (_, true, true) => {
                tracing::debug!("Applying the migration");
                let saved_migration = migration_persistence.create(migration).await?;

                connector
                    .migration_applier()
                    .apply(&saved_migration, &database_migration)
                    .await?;

                tracing::debug!("Migration applied");
            }
            // We have warnings, but no force flag was passed.
            (_, true, false) => tracing::info!("The force flag was not passed, the migration will not be applied."),
        }

        let DestructiveChangeDiagnostics {
            warnings,
            unexecutable_migrations,
        } = diagnostics;

        Ok(MigrationStepsResultOutput {
            datamodel: datamodel::render_datamodel_to_string(&next_datamodel.subject).unwrap(),
            datamodel_steps: self.input.steps.clone(),
            database_steps: database_steps_json_pretty,
            errors: [],
            warnings,
            general_errors: [],
            unexecutable_migrations,
        })
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ApplyMigrationInput {
    pub migration_id: String,
    pub steps: Vec<MigrationStep>,
    pub force: Option<bool>,
}

impl IsWatchMigration for ApplyMigrationInput {
    fn is_watch_migration(&self) -> bool {
        self.migration_id.starts_with("watch")
    }
}
