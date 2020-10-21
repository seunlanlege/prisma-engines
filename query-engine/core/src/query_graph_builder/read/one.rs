use super::*;
use crate::{query_document::*, ReadQuery, RecordQuery};
use prisma_models::ModelRef;
use std::convert::TryInto;

pub struct ReadOneRecordBuilder {
    field: ParsedField,
    model: ModelRef,
}

impl ReadOneRecordBuilder {
    pub fn new(field: ParsedField, model: ModelRef) -> Self {
        Self { field, model }
    }
}

impl Builder<ReadQuery> for ReadOneRecordBuilder {
    /// Builds a read query tree from a parsed top-level field of a query
    /// Unwraps are safe because of query validation that ensures conformity to the query schema.
    fn build(mut self) -> QueryGraphBuilderResult<ReadQuery> {
        let filter = match self.field.arguments.lookup("where") {
            Some(where_arg) => {
                let arg: ParsedInputMap = where_arg.value.try_into()?;
                Some(extractors::extract_unique_filter(arg, &self.model)?)
            }
            None => None,
        };

        let name = self.field.name;
        let alias = self.field.alias;
        let model = self.model;
        let nested_fields = self.field.nested_fields.unwrap().fields;
        let selection_order: Vec<String> = collect_selection_order(&nested_fields);
        let selected_fields = collect_selected_fields(&nested_fields, &model);
        let nested = collect_nested_queries(nested_fields, &model)?;
        let selected_fields = merge_relation_selections(selected_fields, None, &nested);

        Ok(ReadQuery::RecordQuery(RecordQuery {
            name,
            alias,
            model,
            filter,
            selected_fields,
            nested,
            selection_order,
        }))
    }
}
