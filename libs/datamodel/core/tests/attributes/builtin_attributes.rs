use crate::common::*;
use datamodel::{ast, diagnostics::DatamodelError, ScalarType};

#[test]
fn unique_attribute() {
    let dml = r#"
        model Test {
            id Int @id
            unique String @unique
        }
    "#;

    let schema = parse(dml);
    let test_model = schema.assert_has_model("Test");

    test_model
        .assert_has_scalar_field("id")
        .assert_base_type(&ScalarType::Int)
        .assert_is_unique(false)
        .assert_is_id();
    test_model
        .assert_has_scalar_field("unique")
        .assert_base_type(&ScalarType::String)
        .assert_is_unique(true);
}

#[test]
fn duplicate_attributes_should_error() {
    let dml = r#"
        model Test {
            id String @id
            unique String @unique @unique
        }
    "#;

    let error = parse_error(dml);

    error.assert_is_at(
        0,
        DatamodelError::new_duplicate_attribute_error("unique", ast::Span::new(75, 81)),
    );
    error.assert_is_at(
        1,
        DatamodelError::new_duplicate_attribute_error("unique", ast::Span::new(83, 89)),
    );
}
