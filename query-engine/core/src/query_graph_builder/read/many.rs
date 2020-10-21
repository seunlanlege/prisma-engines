use super::*;
use crate::{query_document::ParsedField, ManyRecordsQuery, ReadQuery};
use prisma_models::ModelRef;

pub struct ReadManyRecordsBuilder {
    field: ParsedField,
    model: ModelRef,
}

impl ReadManyRecordsBuilder {
    pub fn new(field: ParsedField, model: ModelRef) -> Self {
        Self { field, model }
    }
}

impl Builder<ReadQuery> for ReadManyRecordsBuilder {
    fn build(self) -> QueryGraphBuilderResult<ReadQuery> {
        let args = extractors::extract_query_args(self.field.arguments, &self.model)?;
        let name = self.field.name;
        let alias = self.field.alias;
        let nested_fields = self.field.nested_fields.unwrap().fields;
        let selection_order: Vec<String> = collect_selection_order(&nested_fields);
        let selected_fields = collect_selected_fields(&nested_fields, &self.model);
        let nested = collect_nested_queries(nested_fields, &self.model)?;
        let model = self.model;

        let selected_fields = merge_relation_selections(selected_fields, None, &nested);
        let selected_fields = merge_cursor_fields(selected_fields, &args.cursor);

        Ok(ReadQuery::ManyRecordsQuery(ManyRecordsQuery {
            name,
            alias,
            model,
            args,
            selected_fields,
            nested,
            selection_order,
        }))
    }
}
