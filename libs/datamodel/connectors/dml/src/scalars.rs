use std::str::FromStr;

use serde::{Deserialize, Serialize};

/// Prisma's builtin scalar types.
#[derive(Debug, Copy, PartialEq, Clone, Serialize, Deserialize, Eq, Hash)]
pub enum ScalarType {
    Int,
    Float,
    Boolean,
    String,
    DateTime,
    Json,
    XML,
    Bytes,
    Decimal,
    Duration,
}

impl FromStr for ScalarType {
    type Err = String;
    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "Int" => Ok(ScalarType::Int),
            "Float" => Ok(ScalarType::Float),
            "Boolean" => Ok(ScalarType::Boolean),
            "String" => Ok(ScalarType::String),
            "DateTime" => Ok(ScalarType::DateTime),
            "Json" => Ok(ScalarType::Json),
            "XML" => Ok(ScalarType::XML),
            "Bytes" => Ok(ScalarType::Bytes),
            "Decimal" => Ok(ScalarType::Decimal),
            "Duration" => Ok(ScalarType::Duration),
            _ => Err(format!("type {} is not a known scalar type.", s)),
        }
    }
}

impl ToString for ScalarType {
    fn to_string(&self) -> String {
        match self {
            ScalarType::Int => String::from("Int"),
            ScalarType::Float => String::from("Float"),
            ScalarType::Boolean => String::from("Boolean"),
            ScalarType::String => String::from("String"),
            ScalarType::DateTime => String::from("DateTime"),
            ScalarType::Json => String::from("Json"),
            ScalarType::XML => String::from("XML"),
            ScalarType::Bytes => String::from("Bytes"),
            ScalarType::Decimal => String::from("Decimal"),
            ScalarType::Duration => String::from("Duration"),
        }
    }
}
