use crate::common::*;
use datamodel::ast::Span;
use datamodel::diagnostics::DatamodelError;
use datamodel::{dml, ScalarType};

#[test]
fn resolve_relation() {
    let dml = r#"
    model User {
        id Int @id
        firstName String
        posts Post[]
    }

    model Post {
        id     Int    @id
        text   String
        userId Int
        
        user User @relation(fields: [userId], references: [id])
    }
    "#;

    let schema = parse(dml);
    let user_model = schema.assert_has_model("User");
    user_model
        .assert_has_scalar_field("firstName")
        .assert_base_type(&ScalarType::String);
    user_model
        .assert_has_relation_field("posts")
        .assert_relation_to("Post")
        .assert_arity(&dml::FieldArity::List);

    let post_model = schema.assert_has_model("Post");
    post_model
        .assert_has_scalar_field("text")
        .assert_base_type(&ScalarType::String);
    post_model.assert_has_relation_field("user").assert_relation_to("User");
}

#[test]
fn resolve_related_field() {
    let dml = r#"
    model User {
        id        Int    @id
        firstName String @unique
        posts Post[]
    }

    model Post {
        id            Int    @id
        text          String
        userFirstName String
        user          User   @relation(fields: [userFirstName], references: [firstName])
    }
    "#;

    let schema = parse(dml);

    let post_model = schema.assert_has_model("Post");
    post_model
        .assert_has_relation_field("user")
        .assert_relation_to("User")
        .assert_relation_to_fields(&["firstName"]);
}

#[test]
fn resolve_related_fields() {
    let dml = r#"
    model User {
        id Int @id
        firstName String
        lastName String
        posts Post[]
        
        @@unique([firstName, lastName])
    }

    model Post {
        id Int @id
        text String
        authorFirstName String
        authorLastName  String
        user            User @relation(fields: [authorFirstName, authorLastName], references: [firstName, lastName])
    }
    "#;

    let schema = parse(dml);

    let post_model = schema.assert_has_model("Post");
    post_model
        .assert_has_relation_field("user")
        .assert_relation_to("User")
        .assert_relation_base_fields(&["authorFirstName", "authorLastName"])
        .assert_relation_to_fields(&["firstName", "lastName"]);
}

#[test]
fn must_error_when_non_existing_fields_are_used() {
    let dml = r#"
    model User {
        id Int @id
        firstName String
        lastName String
        posts Post[]
        
        @@unique([firstName, lastName])
    }

    model Post {
        id   Int    @id
        text String
        user User   @relation(fields: [authorFirstName, authorLastName], references: [firstName, lastName])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is(
        DatamodelError::new_validation_error(
            "The argument fields must refer only to existing fields. The following fields do not exist in this model: authorFirstName, authorLastName", 
                Span::new(232, 332)
        )
    );
}

#[test]
fn resolve_enum_field() {
    let dml = r#"
    model User {
        id Int @id
        email String
        role Role
    }

    enum Role {
        ADMIN
        USER
        PRO
    }
    "#;

    let schema = parse(dml);
    let user_model = schema.assert_has_model("User");
    user_model
        .assert_has_scalar_field("email")
        .assert_base_type(&ScalarType::String);
    user_model.assert_has_scalar_field("role").assert_enum_type("Role");

    let role_enum = schema.assert_has_enum("Role");
    role_enum.assert_has_value("ADMIN");
    role_enum.assert_has_value("PRO");
    role_enum.assert_has_value("USER");
}
