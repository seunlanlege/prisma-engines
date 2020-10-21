use super::{
    helpers::{parsing_catch_all, ToIdentifier, Token, TokenExtensions},
    parse_attribute::parse_attribute,
    parse_comments::*,
    parse_field::parse_field,
    Rule,
};
use crate::ast::*;
use crate::diagnostics::{DatamodelError, Diagnostics};

pub fn parse_model(token: &Token) -> Result<Model, Diagnostics> {
    let mut errors = Diagnostics::new();
    let mut name: Option<Identifier> = None;
    let mut attributes: Vec<Attribute> = vec![];
    let mut fields: Vec<Field> = vec![];
    let mut comment: Option<Comment> = None;

    for current in token.relevant_children() {
        match current.as_rule() {
            Rule::TYPE_KEYWORD => errors.push_error(DatamodelError::new_legacy_parser_error(
                "Model declarations have to be indicated with the `model` keyword.",
                Span::from_pest(current.as_span()),
            )),
            Rule::non_empty_identifier => name = Some(current.to_id()),
            Rule::block_level_attribute => attributes.push(parse_attribute(&current)),
            Rule::field_declaration => match parse_field(&name.as_ref().unwrap().name, &current) {
                Ok(field) => fields.push(field),
                Err(err) => errors.push_error(err),
            },
            Rule::comment_block => comment = Some(parse_comment_block(&current)),
            Rule::BLOCK_LEVEL_CATCH_ALL => errors.push_error(DatamodelError::new_validation_error(
                "This line is not a valid field or attribute definition.",
                Span::from_pest(current.as_span()),
            )),
            _ => parsing_catch_all(&current, "model"),
        }
    }

    errors.to_result()?;

    match name {
        Some(name) => Ok(Model {
            name,
            fields,
            attributes,
            documentation: comment,
            span: Span::from_pest(token.as_span()),
            commented_out: false,
        }),
        _ => panic!(
            "Encountered impossible model declaration during parsing: {:?}",
            token.as_str()
        ),
    }
}
