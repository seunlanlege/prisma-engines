use crate::common::*;
use datamodel::ast::Span;
use datamodel::diagnostics::DatamodelError;
use datamodel::dml;

#[test]
fn relation_happy_path() {
    let dml = r#"
    model User {
        id Int @id
        firstName String
        posts Post[]
    }

    model Post {
        id Int @id
        text String
        userId Int
        user User @relation(fields: [userId], references: [id])
    }
    "#;

    let schema = parse(dml);
    let user_model = schema.assert_has_model("User");
    user_model
        .assert_has_relation_field("posts")
        .assert_arity(&dml::FieldArity::List)
        .assert_relation_to("Post")
        .assert_relation_base_fields(&[])
        .assert_relation_to_fields(&[]);

    let post_model = schema.assert_has_model("Post");
    post_model
        .assert_has_relation_field("user")
        .assert_arity(&dml::FieldArity::Required)
        .assert_relation_to("User")
        .assert_relation_base_fields(&["userId"])
        .assert_relation_to_fields(&["id"]);
}

#[test]
fn relation_must_error_when_base_field_does_not_exist() {
    let dml = r#"
    model User {
        id Int @id
        firstName String
        posts Post[]
    }

    model Post {
        id Int @id
        text String        
        user User @relation(fields: [userId], references: [id])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is(DatamodelError::new_validation_error("The argument fields must refer only to existing fields. The following fields do not exist in this model: userId", Span::new(162, 218)));
}

#[test]
fn relation_must_error_when_base_field_is_not_scalar() {
    let dml = r#"
    model User {
        id Int @id
        firstName String
        posts Post[]
    }

    model Post {
        id Int @id
        text String
        userId Int
        otherId Int        
        
        user User @relation(fields: [other], references: [id])
        other OtherModel @relation(fields: [otherId], references: [id])
    }
    
    model OtherModel {
        id Int @id
        posts Post[]
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is_at(0,DatamodelError::new_validation_error("The argument fields must refer only to scalar fields. But it is referencing the following relation fields: other", Span::new(210, 265)));
    errors.assert_is_at(1,DatamodelError::new_attribute_validation_error("The type of the field `other` in the model `Post` is not matching the type of the referenced field `id` in model `User`.", "relation", Span::new(210, 265)));
}

#[test]
fn optional_relation_field_must_succeed_when_all_underlying_fields_are_optional() {
    let dml = r#"
    model User {
        id        Int     @id
        firstName String?
        lastName  String?
        posts     Post[]
        
        @@unique([firstName, lastName])
    }

    model Post {
        id            Int     @id
        text          String
        userFirstName String?
        userLastName  String?
          
        user          User?   @relation(fields: [userFirstName, userLastName], references: [firstName, lastName])
    }
    "#;

    // must not crash
    let _ = parse(dml);
}

#[test]
fn optional_relation_field_must_error_when_one_underlying_field_is_required() {
    let dml = r#"
    model User {
        id        Int     @id
        firstName String
        lastName  String?
        posts     Post[]
        
        @@unique([firstName, lastName])
    }

    model Post {
        id            Int     @id
        text          String
        userFirstName String
        userLastName  String?
          
        user          User?   @relation(fields: [userFirstName, userLastName], references: [firstName, lastName])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is(DatamodelError::new_validation_error("The relation field `user` uses the scalar fields userFirstName, userLastName. At least one of those fields is required. Hence the relation field must be required as well.", Span::new(338, 444)));
}

#[test]
fn required_relation_field_must_succeed_when_at_least_one_underlying_fields_is_required() {
    let dml = r#"
    model User {
        id        Int     @id
        firstName String
        lastName  String?
        posts     Post[]
        
        @@unique([firstName, lastName])
    }

    model Post {
        id            Int     @id
        text          String
        userFirstName String
        userLastName  String?
          
        user          User    @relation(fields: [userFirstName, userLastName], references: [firstName, lastName])
    }
    "#;

    // must not crash
    let _ = parse(dml);
}

#[test]
fn required_relation_field_must_error_when_all_underlying_fields_are_optional() {
    let dml = r#"
    model User {
        id        Int     @id
        firstName String?
        lastName  String?
        posts     Post[]
        
        @@unique([firstName, lastName])
    }

    model Post {
        id            Int     @id
        text          String
        userFirstName String?
        userLastName  String?
          
        user          User    @relation(fields: [userFirstName, userLastName], references: [firstName, lastName])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is(DatamodelError::new_validation_error("The relation field `user` uses the scalar fields userFirstName, userLastName. All those fields are optional. Hence the relation field must be optional as well.", Span::new(340, 446)));
}

#[test]
fn relation_must_error_when_referenced_field_does_not_exist() {
    let dml = r#"
    model User {
        id Int @id
        firstName String
        posts Post[]
    }

    model Post {
        id Int @id
        text String
        userId Int        
        user User @relation(fields: [userId], references: [fooBar])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is(DatamodelError::new_validation_error("The argument `references` must refer only to existing fields in the related model `User`. The following fields do not exist in the related model: fooBar", Span::new(181, 241)));
}

#[test]
fn relation_must_error_on_mssql_when_referenced_field_is_nullable() {
    let dml = r#"
    datasource db {
       provider = "sqlserver"
       url = "sqlserver://foo"
    }

    model User {
        id String   @id @default(uuid())
        fooBar  String?  @unique
        posts Post[]
    }

    model Post {
        id  String  @id @default(uuid())
        text String?
        user   User? @relation(fields: [text], references: [fooBar])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is(DatamodelError::new_validation_error("The argument `references` must not refer to a nullable field in the related model `User`. But it is referencing the following fields that are nullable: fooBar", Span::new(295, 356)));
}

#[test]
fn relation_must_not_error_on_postgres_when_referenced_field_is_nullable() {
    let dml = r#"
    datasource db {
       provider = "postgres"
       url = "postgresql://foo"
    }

    model User {
        id String   @id @default(uuid())
        fooBar  String?  @unique
        posts Post[]
    }

    model Post {
        id  String  @id @default(uuid())
        text String?
        user   User? @relation(fields: [text], references: [fooBar])
    }
    "#;

    // should not error
    let _ = parse(dml);
}

#[test]
fn relation_must_error_when_referenced_field_is_not_scalar() {
    let dml = r#"
    model User {
        id Int @id
        firstName String
        posts Post[]
    }

    model Post {
        id Int @id
        text String
        userId Int        
        user User @relation(fields: [userId], references: [posts])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is(DatamodelError::new_validation_error("The argument `references` must refer only to scalar fields in the related model `User`. But it is referencing the following relation fields: posts", Span::new(181, 240)));
}

#[test]
fn relation_must_error_when_referenced_fields_are_not_a_unique_criteria() {
    let dml = r#"
    model User {
        id        Int    @id
        firstName String
        posts     Post[]
    }

    model Post {
        id       Int    @id
        text     String
        userName String        
        user     User   @relation(fields: [userName], references: [firstName])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is(DatamodelError::new_validation_error("The argument `references` must refer to a unique criteria in the related model `User`. But it is referencing the following fields that are not a unique criteria: firstName", Span::new(213, 284)));
}

#[allow(non_snake_case)]
#[test]
fn relation_must_NOT_error_when_referenced_fields_are_not_a_unique_criteria_on_mysql() {
    // MySQL allows foreign key to references a non unique criteria
    // https://stackoverflow.com/questions/588741/can-a-foreign-key-reference-a-non-unique-index
    let dml = r#"
    datasource db {
        provider = "mysql"
        url = "mysql://localhost:3306"
    }
    
    model User {
        id        Int    @id
        firstName String
        posts     Post[]
    }

    model Post {
        id       Int    @id
        text     String
        userName String        
        user     User   @relation(fields: [userName], references: [firstName])
    }
    "#;

    let _ = parse(dml);
}

#[test]
fn relation_must_error_when_referenced_fields_are_multiple_uniques() {
    let dml = r#"
    model User {
        id Int @id
        firstName String @unique
        posts Post[]
    }

    model Post {
        id       Int    @id
        text     String
        userId   Int
        userName String        
        // the relation is referencing two uniques. That is too much.
        user User @relation(fields: [userId, userName], references: [id, firstName])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is(DatamodelError::new_validation_error("The argument `references` must refer to a unique criteria in the related model `User`. But it is referencing the following fields that are not a unique criteria: id, firstName", Span::new(298, 375)));
}

#[test]
fn relation_must_error_when_types_of_base_field_and_referenced_field_do_not_match() {
    let dml = r#"
    model User {
        id        Int @id
        firstName String
        posts     Post[]
    }

    model Post {
        id     Int     @id
        userId String  // this type does not match
        user   User    @relation(fields: [userId], references: [id])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is(DatamodelError::new_attribute_validation_error("The type of the field `userId` in the model `Post` is not matching the type of the referenced field `id` in model `User`.", "relation", Span::new(204, 265)));
}

#[test]
fn relation_must_error_when_number_of_fields_and_references_is_not_equal() {
    let dml = r#"
    model User {
        id        Int @id
        firstName String
        posts     Post[]
    }

    model Post {
        id       Int     @id
        userId   Int
        userName String
        user     User    @relation(fields: [userId, userName], references: [id])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is(DatamodelError::new_attribute_validation_error(
        "You must specify the same number of fields in `fields` and `references`.",
        "relation",
        Span::new(200, 273),
    ));
}

#[test]
fn relation_must_succeed_when_type_alias_is_used_for_referenced_field() {
    let dml = r#"
    type CustomId = Int @id @default(autoincrement())
    
    model User {
        id        CustomId
        firstName String
        posts     Post[]
    }

    model Post {
        id     Int     @id
        userId Int
        user   User    @relation(fields: [userId], references: [id])
    }
    "#;

    let _ = parse(dml);
}

#[test]
fn must_succeed_when_fields_argument_is_missing_for_one_to_many() {
    let dml = r#"
    model User {
        id        Int @id
        firstName String
        posts     Post[]
    }

    model Post {
        id     Int     @id
        userId Int
        user   User    @relation(references: [id])
    }
    "#;

    let result = parse(dml);
    result
        .assert_has_model("Post")
        .assert_has_relation_field("user")
        .assert_relation_base_fields(&["userId"]);
    result.assert_has_model("User");
}

#[test]
#[ignore]
fn must_error_when_references_argument_is_missing_for_one_to_many() {
    let dml = r#"
    model User {
        id        Int @id
        firstName String
        posts     Post[]
    }

    model Post {
        id     Int     @id
        userId Int
        user   User    @relation(fields: [userId])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is(DatamodelError::new_attribute_validation_error(
        "The relation field `user` on Model `Post` must specify the `references` argument in the @relation attribute.",
        "relation",
        Span::new(172, 214),
    ));
}

#[test]
fn must_error_fields_or_references_argument_is_placed_on_wrong_side_for_one_to_many() {
    let dml = r#"
        datasource pg {
            provider = "postgres"
            url = "postgresql://localhost:5432"
        }
        
        model User {
          id     Int    @id
          postId Int[]
          posts  Post[] @relation(fields: [postId], references: [id])
        }
        
        model Post {
          id     Int   @id
          userId Int?
          user   User? @relation(fields: [userId], references: [id])
        }
    "#;

    let errors = parse_error(dml);
    errors.assert_is(
        DatamodelError::new_attribute_validation_error(
            "The relation field `posts` on Model `User` must not specify the `fields` or `references` argument in the @relation attribute. You must only specify it on the opposite field `user` on model `Post`.",
            "relation", Span::new(208, 268)
        ),
    );
}

#[test]
#[ignore]
fn must_error_when_both_arguments_are_missing_for_one_to_many() {
    let dml = r#"
    model User {
        id        Int @id
        firstName String
        posts     Post[]
    }

    model Post {
        id     Int     @id
        userId Int
        user   User
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is_at(
        0,
        DatamodelError::new_attribute_validation_error(
            "The relation field `user` on Model `Post` must specify the `fields` argument in the @relation attribute.",
            "relation",
            Span::new(172, 183),
        ),
    );
    errors.assert_is_at(1, DatamodelError::new_attribute_validation_error(
        "The relation field `user` on Model `Post` must specify the `references` argument in the @relation attribute.",
        "relation",
        Span::new(172, 183),
    ));
}

#[test]
fn must_error_when_fields_argument_is_missing_for_one_to_one() {
    let dml = r#"
    model User {
        id        Int @id
        firstName String
        post      Post
    }

    model Post {
        id     Int     @id
        userId Int
        user   User    @relation(references: [id])
    }
    "#;

    let result = parse(dml);
    result
        .assert_has_model("Post")
        .assert_has_relation_field("user")
        .assert_relation_base_fields(&["userId"]);
    result
        .assert_has_model("User")
        .assert_has_relation_field("post")
        .assert_relation_base_fields(&[]);
}

#[test]
#[ignore]
fn must_error_when_references_argument_is_missing_for_one_to_one() {
    let dml = r#"
    model User {
        id        Int @id
        firstName String
        post      Post
    }

    model Post {
        id     Int     @id
        userId Int
        user   User    @relation(fields: [userId])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is_at(
        0,
        DatamodelError::new_attribute_validation_error(
            "The relation fields `post` on Model `User` and `user` on Model `Post` do not provide the `references` argument in the @relation attribute. You have to provide it on one of the two fields.",
            "relation", Span::new(77, 91)
        ),
    );
    errors.assert_is_at(
        1,
        DatamodelError::new_attribute_validation_error(
            "The relation fields `user` on Model `Post` and `post` on Model `User` do not provide the `references` argument in the @relation attribute. You have to provide it on one of the two fields.",
            "relation", Span::new(170, 212)
        ),
    );
}

#[test]
fn must_error_when_fields_and_references_argument_are_placed_on_different_sides_for_one_to_one() {
    let dml = r#"
    model User {
        id        Int @id
        firstName String
        postId    Int
        post      Post @relation(references: [id])
    }

    model Post {
        id     Int     @id
        userId Int
        user   User    @relation(fields: [userId])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is_at(
        0,
        DatamodelError::new_attribute_validation_error(
            "The relation field `post` on Model `User` provides the `references` argument in the @relation attribute. And the related field `user` on Model `Post` provides the `fields` argument. You must provide both arguments on the same side.",
            "relation", Span::new(99, 142)
        ),
    );
    errors.assert_is_at(
        1,
        DatamodelError::new_attribute_validation_error(
            "The relation field `user` on Model `Post` provides the `fields` argument in the @relation attribute. And the related field `post` on Model `User` provides the `references` argument. You must provide both arguments on the same side.",
            "relation", Span::new(220, 263)
        ),
    );
}

#[test]
fn must_error_when_fields_or_references_argument_is_placed_on_both_sides_for_one_to_one() {
    let dml = r#"
    model User {
        id        Int @id
        firstName String
        postId    Int
        post      Post @relation(fields: [postId], references: [id])
    }

    model Post {
        id     Int     @id
        userId Int
        user   User    @relation(fields: [userId], references: [id])
    }
    "#;

    let errors = parse_error(dml);
    errors.assert_is_at(
            0,
            DatamodelError::new_attribute_validation_error(
                "The relation fields `post` on Model `User` and `user` on Model `Post` both provide the `references` argument in the @relation attribute. You have to provide it only on one of the two fields.",
                "relation", Span::new(99, 160)
            ),
        );
    errors.assert_is_at(
            1,
            DatamodelError::new_attribute_validation_error(
                "The relation fields `post` on Model `User` and `user` on Model `Post` both provide the `fields` argument in the @relation attribute. You have to provide it only on one of the two fields.",
                "relation", Span::new(99, 160)
            ),
        );

    errors.assert_is_at(
        2,
        DatamodelError::new_attribute_validation_error(
            "The relation fields `user` on Model `Post` and `post` on Model `User` both provide the `references` argument in the @relation attribute. You have to provide it only on one of the two fields.",
            "relation", Span::new(238, 299)
        ),
    );

    errors.assert_is_at(
        3,
        DatamodelError::new_attribute_validation_error(
            "The relation fields `user` on Model `Post` and `post` on Model `User` both provide the `fields` argument in the @relation attribute. You have to provide it only on one of the two fields.",
            "relation", Span::new(238,299)
        ),
    );
}

#[test]
fn must_error_for_required_one_to_one_self_relations() {
    let dml = r#"
        model User {
          id       Int  @id
          friendId Int
          friend   User @relation("Friends", fields: friendId, references: id)
          friendOf User @relation("Friends")
        }
    "#;
    let errors = parse_error(dml);
    errors.assert_is_at(
        0,
        DatamodelError::new_field_validation_error("The relation fields `friend` and `friendOf` on Model `User` are both required. This is not allowed for a self relation because it would not be possible to create a record.", "User", "friend", Span::new(83, 152)),
    );
    errors.assert_is_at(
        1,
        DatamodelError::new_field_validation_error("The relation fields `friendOf` and `friend` on Model `User` are both required. This is not allowed for a self relation because it would not be possible to create a record.", "User", "friendOf", Span::new(162, 197)),
    );
}

#[test]
fn must_error_when_non_id_field_is_referenced_in_a_many_to_many() {
    let dml = r#"
    model Post {
      id         Int        @id
      slug       Int        @unique 
      categories Category[] @relation(references: [id])
   }
   
   model Category {
     id    Int    @id @default(autoincrement())
     posts Post[] @relation(references: [slug])
   }"#;

    let errors = parse_error(dml);
    errors.assert_is_at(
        0,
        DatamodelError::new_validation_error(
            "Many to many relations must always reference the id field of the related model. Change the argument `references` to use the id field of the related model `Post`. But it is referencing the following fields that are not the id: slug",  
            Span::new(225, 268)
        ),
    );
}

#[test]
fn must_error_nicely_when_a_many_to_many_is_not_possible() {
    // many 2 many is not possible because Post does not have a singular id field
    let dml = r#"
    model Post {
      id         Int        
      slug       Int        @unique 
      categories Category[] @relation(references: [id])
      
      @@id([id, slug])
   }
   
   model Category {
     id    Int    @id @default(autoincrement())
     posts Post[] @relation(references: [slug])
   }"#;

    let errors = parse_error(dml);
    errors.assert_is_at(0, DatamodelError::new_field_validation_error(
        "The relation field `posts` on Model `Category` references `Post` which does not have an `@id` field. Models without `@id` can not be part of a many to many relation. Use an explicit intermediate Model to represent this relationship.",
        "Category",
        "posts",
        Span::new(252, 295)
    ));
}

#[test]
fn must_error_when_many_to_many_is_not_possible_due_to_missing_id() {
    let dml = r#"
    // Post does not have @id
    model Post {
     slug       Int        @unique 
     categories Category[]
   }
   
   model Category {
     id    Int    @id @default(autoincrement())
     posts Post[]
   }"#;

    let errors = parse_error(dml);
    errors.assert_is_at(
        0,
        DatamodelError::new_field_validation_error(
            "The relation field `posts` on Model `Category` references `Post` which does not have an `@id` field. Models without `@id` can not be part of a many to many relation. Use an explicit intermediate Model to represent this relationship.", 
            "Category",
            "posts", 
            Span::new(193, 206)
        ),
    );
}
