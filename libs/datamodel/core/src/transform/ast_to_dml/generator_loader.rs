use super::super::helpers::*;
use crate::ast::Span;
use crate::common::preview_features::{DEPRECATED_GENERATOR_PREVIEW_FEATURES, GENERATOR_PREVIEW_FEATURES};
use crate::transform::ast_to_dml::common::validate_preview_features;
use crate::{ast, configuration::Generator, diagnostics::*};
use std::collections::HashMap;

const PROVIDER_KEY: &str = "provider";
const OUTPUT_KEY: &str = "output";
const BINARY_TARGETS_KEY: &str = "binaryTargets";
const EXPERIMENTAL_FEATURES_KEY: &str = "experimentalFeatures";
const PREVIEW_FEATURES_KEY: &str = "previewFeatures";
const FIRST_CLASS_PROPERTIES: &[&str] = &[
    PROVIDER_KEY,
    OUTPUT_KEY,
    BINARY_TARGETS_KEY,
    EXPERIMENTAL_FEATURES_KEY,
    PREVIEW_FEATURES_KEY,
];

/// Is responsible for loading and validating Generators defined in an AST.
pub struct GeneratorLoader {}

impl GeneratorLoader {
    pub fn load_generators_from_ast(ast_schema: &ast::SchemaAst) -> Result<ValidatedGenerators, Diagnostics> {
        let mut generators: Vec<Generator> = vec![];
        let mut diagnostics = Diagnostics::new();

        for gen in &ast_schema.generators() {
            match Self::lift_generator(&gen) {
                Ok(loaded_gen) => {
                    diagnostics.append_warning_vec(loaded_gen.warnings);
                    generators.push(loaded_gen.subject)
                }
                // Lift error.
                Err(err) => {
                    for e in err.errors {
                        match e {
                            DatamodelError::ArgumentNotFound { argument_name, span } => {
                                diagnostics.push_error(DatamodelError::new_generator_argument_not_found_error(
                                    argument_name.as_str(),
                                    gen.name.name.as_str(),
                                    span,
                                ));
                            }
                            _ => {
                                diagnostics.push_error(e);
                            }
                        }
                    }
                    diagnostics.append_warning_vec(err.warnings)
                }
            }
        }

        if diagnostics.has_errors() {
            Err(diagnostics)
        } else {
            Ok(ValidatedGenerators {
                subject: generators,
                warnings: diagnostics.warnings,
            })
        }
    }

    fn lift_generator(ast_generator: &ast::GeneratorConfig) -> Result<ValidatedGenerator, Diagnostics> {
        let mut args = Arguments::new(&ast_generator.properties, ast_generator.span);
        let mut diagnostics = Diagnostics::new();

        let provider = args.arg(PROVIDER_KEY)?.as_str()?;
        let output = if let Ok(arg) = args.arg(OUTPUT_KEY) {
            Some(arg.as_str()?)
        } else {
            None
        };

        let mut properties: HashMap<String, String> = HashMap::new();

        let binary_targets = match args.arg(BINARY_TARGETS_KEY).ok() {
            Some(x) => x.as_array().to_str_vec()?,
            None => Vec::new(),
        };

        // for compatibility reasons we still accept the old experimental key
        let preview_features_arg = args
            .arg(PREVIEW_FEATURES_KEY)
            .or_else(|_| args.arg(EXPERIMENTAL_FEATURES_KEY));
        let (preview_features, span) = match preview_features_arg.ok() {
            Some(x) => (x.as_array().to_str_vec()?, x.span()),
            None => (Vec::new(), Span::empty()),
        };

        if !preview_features.is_empty() {
            let mut result = validate_preview_features(
                preview_features.clone(),
                span,
                Vec::from(GENERATOR_PREVIEW_FEATURES),
                Vec::from(DEPRECATED_GENERATOR_PREVIEW_FEATURES),
            );
            diagnostics.append(&mut result);
            if diagnostics.has_errors() {
                return Err(diagnostics);
            }
        }

        for prop in &ast_generator.properties {
            let is_first_class_prop = FIRST_CLASS_PROPERTIES.iter().any(|k| *k == prop.name.name);
            if is_first_class_prop {
                continue;
            }

            properties.insert(prop.name.name.clone(), prop.value.to_string());
        }

        Ok(ValidatedGenerator {
            subject: Generator {
                name: ast_generator.name.name.clone(),
                provider,
                output,
                binary_targets,
                preview_features,
                config: properties,
                documentation: ast_generator.documentation.clone().map(|comment| comment.text),
            },
            warnings: diagnostics.warnings,
        })
    }
}
