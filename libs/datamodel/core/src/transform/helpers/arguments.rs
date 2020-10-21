use super::ValueValidator;
use crate::ast;
use crate::diagnostics::{DatamodelError, Diagnostics};
use std::collections::HashSet;

/// Represents a list of arguments.
#[derive(Debug)]
pub struct Arguments<'a> {
    arguments: &'a [ast::Argument],
    used_arguments: HashSet<&'a str>,
    span: ast::Span,
}

impl<'a> Arguments<'a> {
    /// Creates a new instance, given a list of arguments.
    pub fn new(arguments: &'a [ast::Argument], span: ast::Span) -> Arguments<'a> {
        Arguments {
            used_arguments: HashSet::new(),
            arguments,
            span,
        }
    }

    /// Checks if arguments occur twice and returns an appropriate error list.
    pub fn check_for_duplicate_named_arguments(&self) -> Result<(), Diagnostics> {
        let mut arg_names: HashSet<&'a str> = HashSet::new();
        let mut errors = Diagnostics::new();

        for arg in self.arguments {
            if arg_names.contains::<&str>(&(&arg.name.name as &str)) {
                errors.push_error(DatamodelError::new_duplicate_argument_error(&arg.name.name, arg.span));
            }
            if !arg.is_unnamed() {
                arg_names.insert(&arg.name.name);
            }
        }

        errors.to_result()
    }

    pub fn check_for_multiple_unnamed_arguments(&self, attribute_name: &str) -> Result<(), Diagnostics> {
        let mut unnamed_values: Vec<String> = Vec::new();
        for arg in self.arguments {
            if arg.is_unnamed() {
                unnamed_values.push(arg.value.render_to_string());
            }
        }

        if unnamed_values.len() > 1 {
            Err(DatamodelError::new_attribute_validation_error(
                &format!("You provided multiple unnamed arguments. This is not possible. Did you forget the brackets? Did you mean `[{}]`?", unnamed_values.join(", ")),
                attribute_name,
                self.span).into()
            )
        } else {
            Ok(())
        }
    }

    /// Checks if arguments were not accessed and raises the appropriate errors.
    pub fn check_for_unused_arguments(&self) -> Result<(), Diagnostics> {
        let mut errors = Diagnostics::new();

        for arg in self.arguments {
            if !self.used_arguments.contains::<&str>(&(&arg.name.name as &str)) {
                errors.push_error(DatamodelError::new_unused_argument_error(&arg.name.name, arg.span));
            }
        }

        errors.to_result()
    }

    /// Gets the span of all arguments wrapped by this instance.
    pub fn span(&self) -> ast::Span {
        self.span
    }

    /// Gets the arg with the given name.
    pub fn arg(&mut self, name: &str) -> Result<ValueValidator, DatamodelError> {
        match self.arg_internal(name) {
            None => Err(DatamodelError::new_argument_not_found_error(name, self.span)),
            Some(arg) => Ok(ValueValidator::new(&arg.value)),
        }
    }

    pub fn optional_arg(&mut self, name: &str) -> Option<ValueValidator> {
        match self.arg_internal(name) {
            None => None,
            Some(arg) => Some(ValueValidator::new(&arg.value)),
        }
    }

    /// Gets the full argument span for an argument, used to generate errors.
    fn arg_internal(&mut self, name: &str) -> Option<&'a ast::Argument> {
        for arg in self.arguments {
            if arg.name.name == name {
                self.used_arguments.insert(&arg.name.name as &str);
                return Some(&arg);
            }
        }

        None
    }

    /// Gets the arg with the given name, or if it is not found, the first unnamed argument.
    ///
    /// Use this to implement unnamed argument behavior.
    pub fn default_arg(&mut self, name: &str) -> Result<ValueValidator, DatamodelError> {
        match (self.arg_internal(name), self.arg_internal("")) {
            (Some(arg), None) => Ok(ValueValidator::new(&arg.value)),
            (None, Some(arg)) => Ok(ValueValidator::new(&arg.value)),
            (Some(arg), Some(_)) => Err(DatamodelError::new_duplicate_default_argument_error(&name, arg.span)),
            (None, None) => Err(DatamodelError::new_argument_not_found_error(name, self.span)),
        }
    }
}
