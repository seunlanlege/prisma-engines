mod directives;
mod enum_values;
mod enums;
mod fields;
mod models;
mod source;
mod top_level;

use directives::DirectiveDiffer;
use enum_values::EnumValueDiffer;
use enums::EnumDiffer;
use fields::FieldDiffer;
use models::ModelDiffer;
use top_level::TopDiffer;

use crate::migration::datamodel_differ::source::SourceArgumentsDiffer;
use datamodel::ast;
use datamodel::ast::Expression;
use migration_connector::steps::{
    self, ArgumentLocation, DirectiveLocation, DirectivePath, MigrationStep, SourceLocation,
};

/// Diff two datamodels, returning the [MigrationStep](/struct.MigrationStep.html)s from `previous`
/// to `next`.
pub(crate) fn diff(previous: &ast::SchemaAst, next: &ast::SchemaAst) -> Vec<MigrationStep> {
    let mut steps = Vec::new();
    let differ = TopDiffer { previous, next };

    push_type_aliases(&mut steps, &differ);
    push_enums(&mut steps, &differ);
    push_datasources(&mut steps, &differ);
    push_models(&mut steps, &differ);

    steps
}

type Steps = Vec<MigrationStep>;

fn push_type_aliases(steps: &mut Steps, differ: &TopDiffer<'_>) {
    push_created_type_aliases(steps, differ.created_type_aliases());
    push_deleted_type_aliases(steps, differ.deleted_type_aliases());
    push_updated_type_aliases(steps, differ.type_alias_pairs());
}

fn push_created_type_aliases<'a>(steps: &mut Steps, type_aliases: impl Iterator<Item = &'a ast::Field>) {
    for created_type_alias in type_aliases {
        let create_type_alias_step = steps::CreateTypeAlias {
            type_alias: created_type_alias.name.name.clone(),
            r#type: created_type_alias.field_type.name.clone(),
            arity: created_type_alias.arity.into(),
        };

        steps.push(MigrationStep::CreateTypeAlias(create_type_alias_step));

        let location = steps::DirectivePath::TypeAlias {
            type_alias: created_type_alias.name.name.clone(),
        };

        push_created_directives(steps, &location, created_type_alias.attributes.iter())
    }
}

fn push_deleted_type_aliases<'a>(steps: &mut Steps, type_aliases: impl Iterator<Item = &'a ast::Field>) {
    let delete_type_alias_steps = type_aliases
        .map(|deleted_type_alias| steps::DeleteTypeAlias {
            type_alias: deleted_type_alias.name.name.clone(),
        })
        .map(MigrationStep::DeleteTypeAlias);

    steps.extend(delete_type_alias_steps)
}

fn push_updated_type_aliases<'a>(steps: &mut Steps, type_aliases: impl Iterator<Item = FieldDiffer<'a>>) {
    for updated_type_alias in type_aliases {
        let directive_path = steps::DirectivePath::TypeAlias {
            type_alias: updated_type_alias.previous.name.name.clone(),
        };

        let step = steps::UpdateTypeAlias {
            type_alias: updated_type_alias.previous.name.name.clone(),
            r#type: diff_value(
                &updated_type_alias.previous.field_type.name,
                &updated_type_alias.next.field_type.name,
            ),
        };

        if step.is_any_option_set() {
            steps.push(MigrationStep::UpdateTypeAlias(step));
        }

        push_created_directives(steps, &directive_path, updated_type_alias.created_directives());
        push_updated_directives(steps, &directive_path, updated_type_alias.directive_pairs());
        push_deleted_directives(steps, &directive_path, updated_type_alias.deleted_directives());
    }
}

fn push_enums(steps: &mut Steps, differ: &TopDiffer<'_>) {
    push_created_enums(steps, differ.created_enums());
    push_deleted_enums(steps, differ.deleted_enums());
    push_updated_enums(steps, differ.enum_pairs());
}

fn push_created_enums<'a>(steps: &mut Steps, enums: impl Iterator<Item = &'a ast::Enum>) {
    for r#enum in enums {
        let create_enum_step = steps::CreateEnum {
            r#enum: r#enum.name.name.clone(),
            values: r#enum.values.iter().map(|value| value.name.name.clone()).collect(),
        };

        steps.push(MigrationStep::CreateEnum(create_enum_step));

        let directive_path = steps::DirectivePath::Enum {
            r#enum: r#enum.name.name.clone(),
        };

        push_created_directives(steps, &directive_path, r#enum.attributes.iter());

        for value in &r#enum.values {
            let path = steps::DirectivePath::EnumValue {
                r#enum: r#enum.name.name.clone(),
                value: value.name.name.clone(),
            };

            push_created_directives(steps, &path, value.attributes.iter());
        }
    }
}

fn push_deleted_enums<'a>(steps: &mut Steps, enums: impl Iterator<Item = &'a ast::Enum>) {
    let deleted_enum_steps = enums
        .map(|deleted_enum| steps::DeleteEnum {
            r#enum: deleted_enum.name.name.clone(),
        })
        .map(MigrationStep::DeleteEnum);

    steps.extend(deleted_enum_steps)
}

fn push_updated_enums<'a>(steps: &mut Steps, enums: impl Iterator<Item = EnumDiffer<'a>>) {
    for updated_enum in enums {
        let deleted_values: Vec<_> = updated_enum
            .deleted_values()
            .map(|value| value.name.name.to_owned())
            .collect();

        let mut created_values = Vec::new();
        for created_value in updated_enum.created_values() {
            created_values.push(created_value.name.name.to_owned());

            let path = DirectivePath::EnumValue {
                r#enum: updated_enum.next.name.name.clone(),
                value: created_value.name.name.clone(),
            };

            push_created_directives(steps, &path, created_value.attributes.iter());
        }

        for value_differ in updated_enum.value_pairs() {
            push_updated_enum_value(steps, value_differ, &updated_enum.next.name.name);
        }

        let update_enum_step = steps::UpdateEnum {
            r#enum: updated_enum.previous.name.name.clone(),
            new_name: diff_value(&updated_enum.previous.name.name, &updated_enum.next.name.name),
            created_values,
            deleted_values,
        };

        if update_enum_step.is_any_option_set() {
            steps.push(MigrationStep::UpdateEnum(update_enum_step));
        }

        let directive_path = steps::DirectivePath::Enum {
            r#enum: updated_enum.previous.name.name.clone(),
        };

        push_created_directives(steps, &directive_path, updated_enum.created_directives());
        push_updated_directives(steps, &directive_path, updated_enum.directive_pairs());
        push_deleted_directives(steps, &directive_path, updated_enum.deleted_directives());
    }
}

fn push_updated_enum_value(steps: &mut Steps, enum_differ: EnumValueDiffer<'_>, enum_name: &str) {
    let path = DirectivePath::EnumValue {
        r#enum: enum_name.to_owned(),
        value: enum_differ.next.name.name.clone(),
    };

    push_created_directives(steps, &path, enum_differ.created_directives());
    push_updated_directives(steps, &path, enum_differ.directive_pairs());
    push_deleted_directives(steps, &path, enum_differ.deleted_directives());
}

fn push_datasources(steps: &mut Steps, differ: &TopDiffer<'_>) {
    push_created_sources(steps, differ.created_datasources());
    push_deleted_sources(steps, differ.deleted_datasources());
    push_updated_sources(steps, differ.updated_datasources());
}

fn push_updated_sources<'a>(steps: &mut Steps, sources: impl Iterator<Item = SourceArgumentsDiffer<'a>>) {
    for source in sources {
        let location = ArgumentLocation::Source(SourceLocation {
            source: source.previous.name.name.to_owned(),
        });

        for argument in source.created_arguments() {
            push_created_source_argument(steps, &location, argument)
        }

        for argument in source.deleted_arguments() {
            push_deleted_argument(steps, &location.clone(), &argument.name.name);
        }

        for (prev, next) in source.argument_pairs() {
            // we are comparing the schema stored in the Migrations table with the user provided one
            // the one in the Migrations table has the mask. The user provided does not have it.
            // this would lead to unnecessary updates all the time.
            if prev.name.name != "url" {
                push_updated_argument(steps, &location.clone(), prev, next)
            }
        }
    }
}

fn push_created_sources<'a>(steps: &mut Steps, sources: impl Iterator<Item = &'a ast::SourceConfig>) {
    for created_source in sources {
        let create_source_step = steps::CreateSource {
            source: created_source.name.name.clone(),
        };

        steps.push(MigrationStep::CreateSource(create_source_step));

        let location = steps::ArgumentLocation::Source(steps::SourceLocation {
            source: created_source.name.name.clone(),
        });

        for argument in &created_source.properties {
            push_created_source_argument(steps, &location, argument)
        }
    }
}

fn push_created_source_argument(steps: &mut Steps, location: &steps::ArgumentLocation, argument: &ast::Argument) {
    // Datasource URLs should always be masked here. Otherwise they will end up in clear text in `steps.json` or the Readme in the migrations folder.
    if argument.name.name == "url" {
        let mut cloned = argument.clone();
        cloned.value = Expression::StringValue("***".to_string(), argument.value.span());
        push_created_argument(steps, &location, &cloned);
    } else {
        push_created_argument(steps, &location, argument);
    }
}

fn push_deleted_sources<'a>(steps: &mut Steps, sources: impl Iterator<Item = &'a ast::SourceConfig>) {
    let delete_source_steps = sources
        .map(|x| steps::DeleteSource {
            source: x.name.name.clone(),
        })
        .map(MigrationStep::DeleteSource);

    steps.extend(delete_source_steps);
}

fn push_models(steps: &mut Steps, differ: &TopDiffer<'_>) {
    push_created_models(steps, differ.created_models());
    push_deleted_models(steps, differ.deleted_models());
    push_updated_models(steps, differ.model_pairs());
}

fn push_created_models<'a>(steps: &mut Steps, models: impl Iterator<Item = &'a ast::Model>) {
    for created_model in models {
        let directive_path = DirectivePath::Model {
            model: created_model.name.name.clone(),
            arguments: None,
        };

        let create_model_step = steps::CreateModel {
            model: created_model.name.name.clone(),
        };

        steps.push(MigrationStep::CreateModel(create_model_step));

        push_created_fields(steps, &created_model.name.name, created_model.fields.iter());

        push_created_directives(
            steps,
            &directive_path,
            created_model.attributes.iter().filter(models::directive_is_regular),
        );
        push_created_directives_with_arguments(
            steps,
            &directive_path,
            created_model.attributes.iter().filter(models::directive_is_repeated),
        );
    }
}

fn push_deleted_models<'a>(steps: &mut Steps, models: impl Iterator<Item = &'a ast::Model>) {
    let delete_model_steps = models
        .map(|deleted_model| steps::DeleteModel {
            model: deleted_model.name.name.clone(),
        })
        .map(MigrationStep::DeleteModel);

    steps.extend(delete_model_steps);
}

fn push_updated_models<'a>(steps: &mut Steps, models: impl Iterator<Item = ModelDiffer<'a>>) {
    models.for_each(|model| {
        let model_name = &model.previous.name.name;

        push_created_fields(steps, model_name, model.created_fields());
        push_deleted_fields(steps, model_name, model.deleted_fields());
        push_updated_fields(steps, model_name, model.field_pairs());

        let directive_path = DirectivePath::Model {
            model: model_name.clone(),
            arguments: None,
        };

        push_created_directives(steps, &directive_path, model.created_regular_directives());
        push_updated_directives(steps, &directive_path, model.regular_directive_pairs());
        push_deleted_directives(steps, &directive_path, model.deleted_regular_directives());

        for directive in model.created_repeated_directives() {
            push_created_directive_with_arguments(steps, directive_path.clone(), directive)
        }

        for directive in model.deleted_repeated_directives() {
            push_deleted_directive_with_arguments(steps, directive_path.clone(), directive)
        }
    });
}

fn push_created_fields<'a>(steps: &mut Steps, model_name: &'a str, fields: impl Iterator<Item = &'a ast::Field>) {
    for field in fields {
        let create_field_step = steps::CreateField {
            arity: field.arity.into(),
            field: field.name.name.clone(),
            tpe: field.field_type.name.clone(),
            model: model_name.to_owned(),
        };

        steps.push(MigrationStep::CreateField(create_field_step));

        let directive_path = steps::DirectivePath::Field {
            model: model_name.to_owned(),
            field: field.name.name.clone(),
        };

        push_created_directives(steps, &directive_path, field.attributes.iter());
    }
}

fn push_deleted_fields<'a>(steps: &mut Steps, model_name: &'a str, fields: impl Iterator<Item = &'a ast::Field>) {
    let delete_field_steps = fields
        .map(|deleted_field| steps::DeleteField {
            model: model_name.to_owned(),
            field: deleted_field.name.name.clone(),
        })
        .map(MigrationStep::DeleteField);

    steps.extend(delete_field_steps);
}

fn push_updated_fields<'a>(steps: &mut Steps, model_name: &'a str, fields: impl Iterator<Item = FieldDiffer<'a>>) {
    for field in fields {
        let update_field_step = steps::UpdateField {
            arity: diff_value(&field.previous.arity, &field.next.arity).map(Into::into),
            new_name: diff_value(&field.previous.name.name, &field.next.name.name),
            model: model_name.to_owned(),
            field: field.previous.name.name.clone(),
            tpe: diff_value(&field.previous.field_type.name, &field.next.field_type.name),
        };

        if update_field_step.is_any_option_set() {
            steps.push(MigrationStep::UpdateField(update_field_step));
        }

        let directive_path = steps::DirectivePath::Field {
            model: model_name.to_owned(),
            field: field.previous.name.name.clone(),
        };

        push_created_directives(steps, &directive_path, field.created_directives());
        push_updated_directives(steps, &directive_path, field.directive_pairs());
        push_deleted_directives(steps, &directive_path, field.deleted_directives());
    }
}

fn push_created_directives<'a>(
    steps: &mut Steps,
    directive_path: &steps::DirectivePath,
    directives: impl Iterator<Item = &'a ast::Attribute>,
) {
    for directive in directives {
        push_created_directive(steps, directive_path.clone(), directive);
    }
}

fn push_created_directives_with_arguments<'a>(
    steps: &mut Steps,
    directive_path: &steps::DirectivePath,
    directives: impl Iterator<Item = &'a ast::Attribute>,
) {
    for directive in directives {
        push_created_directive_with_arguments(steps, directive_path.clone(), directive);
    }
}

fn push_created_directive_with_arguments(
    steps: &mut Steps,
    directive_path: steps::DirectivePath,
    directive: &ast::Attribute,
) {
    let updated_path = directive_path.set_arguments(directive.arguments.iter().map(steps::Argument::from).collect());
    let step = steps::CreateDirective {
        location: steps::DirectiveLocation {
            path: updated_path,
            directive: directive.name.name.clone(),
        },
    };

    steps.push(MigrationStep::CreateDirective(step));
}

fn push_created_directive(steps: &mut Steps, directive_path: steps::DirectivePath, directive: &ast::Attribute) {
    let directive_location = steps::DirectiveLocation {
        path: directive_path,
        directive: directive.name.name.clone(),
    };
    let argument_location = ArgumentLocation::Directive(directive_location.clone());

    let step = steps::CreateDirective {
        location: directive_location,
    };

    steps.push(MigrationStep::CreateDirective(step));

    for argument in &directive.arguments {
        push_created_argument(steps, &argument_location, argument);
    }
}

fn push_deleted_directives<'a>(
    steps: &mut Steps,
    directive_path: &steps::DirectivePath,
    directives: impl Iterator<Item = &'a ast::Attribute>,
) {
    for directive in directives {
        push_deleted_directive(steps, directive_path.clone(), directive);
    }
}

fn push_deleted_directive(steps: &mut Steps, directive_path: steps::DirectivePath, directive: &ast::Attribute) {
    let location = steps::DirectiveLocation {
        path: directive_path,
        directive: directive.name.name.clone(),
    };
    let step = steps::DeleteDirective { location };

    steps.push(MigrationStep::DeleteDirective(step));
}

fn push_deleted_directive_with_arguments(
    steps: &mut Steps,
    directive_path: steps::DirectivePath,
    directive: &ast::Attribute,
) {
    let updated_path = directive_path.set_arguments(directive.arguments.iter().map(steps::Argument::from).collect());
    let location = steps::DirectiveLocation {
        path: updated_path,
        directive: directive.name.name.clone(),
    };
    let step = steps::DeleteDirective { location };

    steps.push(MigrationStep::DeleteDirective(step));
}

fn push_updated_directives<'a>(
    steps: &mut Steps,
    directive_path: &steps::DirectivePath,
    directives: impl Iterator<Item = DirectiveDiffer<'a>>,
) {
    for directive in directives {
        push_updated_directive(steps, directive_path.clone(), directive);
    }
}

fn push_updated_directive(steps: &mut Steps, directive_path: steps::DirectivePath, directive: DirectiveDiffer<'_>) {
    let location = steps::ArgumentLocation::Directive(DirectiveLocation {
        path: directive_path,
        directive: directive.previous.name.name.clone(),
    });

    for argument in directive.created_arguments() {
        push_created_argument(steps, &location, &argument);
    }

    for (previous, next) in directive.argument_pairs() {
        push_updated_argument(steps, &location, previous, next);
    }

    for argument in directive.deleted_arguments() {
        push_deleted_argument(steps, &location, &argument.name.name);
    }
}

fn push_created_argument(steps: &mut Steps, argument_location: &steps::ArgumentLocation, argument: &ast::Argument) {
    let create_argument_step = steps::CreateArgument {
        argument: argument.name.name.clone(),
        value: steps::MigrationExpression::from_ast_expression(&argument.value),
        location: argument_location.clone(),
    };

    steps.push(MigrationStep::CreateArgument(create_argument_step));
}

fn push_updated_argument(
    steps: &mut Steps,
    directive_location: &steps::ArgumentLocation,
    previous_argument: &ast::Argument,
    next_argument: &ast::Argument,
) {
    let previous_value = steps::MigrationExpression::from_ast_expression(&previous_argument.value);
    let next_value = steps::MigrationExpression::from_ast_expression(&next_argument.value);

    if previous_value == next_value {
        return;
    }

    let update_argument_step = steps::UpdateArgument {
        argument: next_argument.name.name.clone(),
        new_value: next_value,
        location: directive_location.clone(),
    };

    steps.push(MigrationStep::UpdateArgument(update_argument_step));
}

fn push_deleted_argument(steps: &mut Steps, directive_location: &steps::ArgumentLocation, argument: &str) {
    let delete_argument_step = steps::DeleteArgument {
        argument: argument.to_owned(),
        location: directive_location.clone(),
    };

    steps.push(MigrationStep::DeleteArgument(delete_argument_step));
}

fn diff_value<T: PartialEq + Clone>(current: &T, updated: &T) -> Option<T> {
    if current == updated {
        None
    } else {
        Some(updated.clone())
    }
}
