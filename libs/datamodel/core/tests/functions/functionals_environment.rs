use crate::common::*;
use datamodel::{DefaultValue, ScalarType};
use prisma_value::PrismaValue;

#[test]
fn skipping_of_env_vars() {
    let dml = r#"
    datasource db {
        provider = "postgresql"
        url      = env("POSTGRES_URL")
    }
    
    model User {
        id   Int      @id
        tags String[]
    }
    "#;

    // must fail without env var
    parse_error(dml);

    // must not fail when ignore flag is set
    if let Err(err) = datamodel::parse_datamodel_and_ignore_datasource_urls(dml) {
        panic!("Skipping env var errors did not work. Error was {:?}", err)
    }

    // must not fail with invalid env var set and ignore flag is set
    std::env::set_var("POSTGRES_URL", "mysql://"); // wrong protocol
    if let Err(err) = datamodel::parse_datamodel_and_ignore_datasource_urls(dml) {
        panic!("Skipping env var errors did not work. Error was {:?}", err)
    }

    // must not fail with correct env var set
    std::env::set_var("POSTGRES_URL", "postgresql://localhost:5432");
    parse(dml);
}

#[ignore]
#[test]
fn interpolate_environment_variables() {
    let dml = r#"
    model User {
        id Int @id
        firstName String @default(env("TEST_USER"))
        lastName String
    }
    "#;

    std::env::set_var("TEST_USER", "prisma-user");

    let schema = parse(dml);
    let user_model = schema.assert_has_model("User");
    user_model.assert_is_embedded(false);
    user_model
        .assert_has_scalar_field("firstName")
        .assert_base_type(&ScalarType::String)
        .assert_default_value(DefaultValue::Single(PrismaValue::String(String::from("prisma-user"))));
}

// This is very useless, except being a good test case.
#[ignore]
#[test]
fn interpolate_nested_environment_variables() {
    let dml = r#"
    model User {
        id Int @id
        firstName String @default(env(env("TEST_USER_VAR")))
        lastName String
    }
    "#;

    std::env::set_var("TEST_USER_VAR", "TEST_USER");
    std::env::set_var("TEST_USER", "prisma-user");

    let schema = parse(dml);
    let user_model = schema.assert_has_model("User");
    user_model.assert_is_embedded(false);
    user_model
        .assert_has_scalar_field("firstName")
        .assert_base_type(&ScalarType::String)
        .assert_default_value(DefaultValue::Single(PrismaValue::String(String::from("prisma-user"))));
}

#[ignore]
#[test]
fn ducktype_environment_variables() {
    let dml = r#"
    model User {
        id Int @id
        age Int @default(env("USER_AGE"))
        name String
    }
    "#;

    std::env::set_var("USER_AGE", "18");

    let schema = parse(dml);
    let user_model = schema.assert_has_model("User");
    user_model.assert_is_embedded(false);
    user_model
        .assert_has_scalar_field("age")
        .assert_base_type(&ScalarType::Int)
        .assert_default_value(DefaultValue::Single(PrismaValue::Int(18)));
}
