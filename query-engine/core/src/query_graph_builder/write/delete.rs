use super::*;
use crate::{
    query_ast::*,
    query_graph::{QueryGraph, QueryGraphDependency},
    ArgumentListLookup, FilteredQuery, ParsedField, ReadOneRecordBuilder,
};
use connector::filter::Filter;
use prisma_models::ModelRef;
use std::{convert::TryInto, sync::Arc};

/// Creates a top level delete record query and adds it to the query graph.
pub fn delete_record(graph: &mut QueryGraph, model: ModelRef, mut field: ParsedField) -> QueryGraphBuilderResult<()> {
    let where_arg = field.arguments.lookup("where").unwrap();
    let filter = extract_unique_filter(where_arg.value.try_into()?, &model)?;

    // Prefetch read query for the delete
    let mut read_query = ReadOneRecordBuilder::new(field, Arc::clone(&model)).build()?;
    read_query.add_filter(filter.clone());

    let read_node = graph.create_node(Query::Read(read_query));
    let delete_query = Query::Write(WriteQuery::DeleteRecord(DeleteRecord {
        model: Arc::clone(&model),
        record_filter: Some(filter.into()),
    }));

    let delete_node = graph.create_node(delete_query);

    utils::insert_deletion_checks(graph, &model, &read_node, &delete_node)?;

    graph.create_edge(
        &read_node,
        &delete_node,
        QueryGraphDependency::ParentProjection(
            model.primary_identifier(),
            Box::new(|delete_node, parent_ids| {
                if !parent_ids.is_empty() {
                    Ok(delete_node)
                } else {
                    Err(QueryGraphBuilderError::RecordNotFound(
                        "Record to delete does not exist.".to_owned(),
                    ))
                }
            }),
        ),
    )?;

    graph.add_result_node(&read_node);
    Ok(())
}

/// Creates a top level delete many records query and adds it to the query graph.
pub fn delete_many_records(
    graph: &mut QueryGraph,
    model: ModelRef,
    mut field: ParsedField,
) -> QueryGraphBuilderResult<()> {
    let filter = match field.arguments.lookup("where") {
        Some(where_arg) => extract_filter(where_arg.value.try_into()?, &model)?,
        None => Filter::empty(),
    };

    let model_id = model.primary_identifier();
    let read_query = utils::read_ids_infallible(model.clone(), model_id, filter.clone());
    let record_filter = filter.into();
    let delete_many = WriteQuery::DeleteManyRecords(DeleteManyRecords {
        model: model.clone(),
        record_filter,
    });

    let read_query_node = graph.create_node(read_query);
    let delete_many_node = graph.create_node(Query::Write(delete_many));

    utils::insert_deletion_checks(graph, &model, &read_query_node, &delete_many_node)?;
    graph.create_edge(
        &read_query_node,
        &delete_many_node,
        QueryGraphDependency::ExecutionOrder,
    )?;

    Ok(())
}
