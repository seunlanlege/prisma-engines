#![warn(warnings)] // Todo deny warnings once done

#[macro_use]
extern crate tracing;

pub mod error;
pub mod executor;
pub mod interpreter;
pub mod query_ast;
pub mod query_document;
pub mod query_graph;
pub mod query_graph_builder;
pub mod response_ir;
pub mod result_ast;
pub mod schema;
pub mod schema_builder;

pub use error::*;
pub use executor::*;
pub use interpreter::*;
pub use query_ast::*;
pub use query_document::*;
pub use query_graph::*;
pub use query_graph_builder::*;
pub use response_ir::*;
pub use result_ast::*;
pub use schema::*;
pub use schema_builder::*;

/// Result type tying all sub-result type hierarchies of the core together.
pub type Result<T> = std::result::Result<T, CoreError>;
