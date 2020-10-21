use std::str::FromStr;

use crate::{
    ast::{self, WithIdentifier, WithName},
    diagnostics::{DatamodelError, Diagnostics},
    dml,
};

pub struct Precheck {}

impl Precheck {
    pub fn precheck(datamodel: &ast::SchemaAst) -> Result<(), Diagnostics> {
        let mut errors = Diagnostics::new();

        let mut top_level_types_checker = DuplicateChecker::new();
        let mut sources_checker = DuplicateChecker::new();
        let mut generators_checker = DuplicateChecker::new();

        for top in &datamodel.tops {
            let error_fn = |existing: &ast::Top| {
                DatamodelError::new_duplicate_top_error(
                    top.name(),
                    top.get_type(),
                    existing.get_type(),
                    top.identifier().span,
                )
            };
            match top {
                ast::Top::Enum(enum_type) => {
                    Self::assert_is_not_a_reserved_scalar_type(&enum_type.name, &mut errors);
                    top_level_types_checker.check_if_duplicate_exists(top, error_fn);
                    Self::precheck_enum(&enum_type, &mut errors);
                }
                ast::Top::Model(model) => {
                    Self::assert_is_not_a_reserved_scalar_type(&model.name, &mut errors);
                    top_level_types_checker.check_if_duplicate_exists(top, error_fn);
                    Self::precheck_model(&model, &mut errors);
                }
                ast::Top::Type(custom_type) => {
                    Self::assert_is_not_a_reserved_scalar_type(&custom_type.name, &mut errors);
                    top_level_types_checker.check_if_duplicate_exists(top, error_fn);
                }
                ast::Top::Source(source) => {
                    Self::assert_is_not_a_reserved_scalar_type(&source.name, &mut errors);
                    sources_checker.check_if_duplicate_exists(top, error_fn);
                    Self::precheck_source_config(&source, &mut errors);
                }
                ast::Top::Generator(generator) => {
                    Self::assert_is_not_a_reserved_scalar_type(&generator.name, &mut errors);
                    generators_checker.check_if_duplicate_exists(top, error_fn);
                    Self::precheck_generator_config(&generator, &mut errors);
                }
            }
        }

        errors.append(&mut top_level_types_checker.errors());
        errors.append(&mut sources_checker.errors());
        errors.append(&mut generators_checker.errors());

        errors.to_result()
    }

    fn assert_is_not_a_reserved_scalar_type(identifier: &ast::Identifier, errors: &mut Diagnostics) {
        if dml::ScalarType::from_str(&identifier.name).is_ok() {
            errors.push_error(DatamodelError::new_reserved_scalar_type_error(
                &identifier.name,
                identifier.span,
            ));
        }
    }

    fn precheck_enum(enum_type: &ast::Enum, errors: &mut Diagnostics) {
        let mut checker = DuplicateChecker::new();
        for value in &enum_type.values {
            checker.check_if_duplicate_exists(value, |_| {
                DatamodelError::new_duplicate_enum_value_error(&enum_type.name.name, &value.name.name, value.span)
            });
        }
        errors.append(&mut checker.errors());
    }

    fn precheck_model(model: &ast::Model, errors: &mut Diagnostics) {
        let mut checker = DuplicateChecker::new();
        for field in &model.fields {
            checker.check_if_duplicate_exists(field, |_| {
                DatamodelError::new_duplicate_field_error(&model.name.name, &field.name.name, field.identifier().span)
            });
        }
        errors.append(&mut checker.errors());
    }

    fn precheck_generator_config(config: &ast::GeneratorConfig, errors: &mut Diagnostics) {
        let mut checker = DuplicateChecker::new();
        for arg in &config.properties {
            checker.check_if_duplicate_exists(arg, |_| {
                DatamodelError::new_duplicate_config_key_error(
                    &format!("generator configuration \"{}\"", config.name.name),
                    &arg.name.name,
                    arg.identifier().span,
                )
            });
        }
        errors.append(&mut checker.errors());
    }

    fn precheck_source_config(config: &ast::SourceConfig, errors: &mut Diagnostics) {
        let mut checker = DuplicateChecker::new();
        for arg in &config.properties {
            checker.check_if_duplicate_exists(arg, |_| {
                DatamodelError::new_duplicate_config_key_error(
                    &format!("datasource configuration \"{}\"", config.name.name),
                    &arg.name.name,
                    arg.identifier().span,
                )
            });
        }
        errors.append(&mut checker.errors());
    }
}

struct DuplicateChecker<'a, T: WithName> {
    seen: Vec<&'a T>,
    errors: Diagnostics,
}

impl<'a, T: WithName> DuplicateChecker<'a, T> {
    fn new() -> DuplicateChecker<'a, T> {
        DuplicateChecker {
            seen: Vec::new(),
            errors: Diagnostics::new(),
        }
    }

    /// checks if an object with the same name was already seen
    /// if an object with the same name already exists the error function is called
    /// the error returned by the function is then stored
    fn check_if_duplicate_exists<F>(&mut self, named: &'a T, error_fn: F)
    where
        F: Fn(&T) -> DatamodelError,
    {
        match self.seen.iter().find(|x| x.name() == named.name()) {
            Some(existing) => self.errors.push_error(error_fn(existing)),
            None => self.seen.push(named),
        }
    }

    fn errors(self) -> Diagnostics {
        self.errors
    }
}
