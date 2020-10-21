use crate::{
    query_ast::*,
    query_graph::{Node, NodeRef, QueryGraph, QueryGraphDependency},
    QueryGraphBuilderError, QueryGraphBuilderResult,
};
use prisma_models::RelationFieldRef;
use std::sync::Arc;

/// Only for many to many relations.
///
/// Creates a disconnect node in the graph and creates edges to `parent_node` and `child_node`.
/// By default, the connect node assumes that both the parent and the child node results
/// are convertible to IDs, as the edges perform a transformation on the connect node to
/// inject the required IDs after the parents executed.
///
/// The resulting graph (dashed indicates that those nodes and edges are not created in this function):
/// ```text
///    ┌ ─ ─ ─ ─ ─ ─ ─ ─ ┐
/// ┌──      Parent       ─ ─ ─ ─ ─
/// │  └ ─ ─ ─ ─ ─ ─ ─ ─ ┘         │
/// │           │
/// │                              │
/// │           │
/// │           ▼                  ▼
/// │  ┌ ─ ─ ─ ─ ─ ─ ─ ─ ┐  ┌ ─ ─ ─ ─ ─ ─
/// │         Child             Result   │
/// │  └ ─ ─ ─ ─ ─ ─ ─ ─ ┘  └ ─ ─ ─ ─ ─ ─
/// │           │
/// │           │
/// │           │
/// │           ▼
/// │  ┌─────────────────┐
/// └─▶│   Disconnect    │
///    └─────────────────┘
/// ```
pub fn disconnect_records_node(
    graph: &mut QueryGraph,
    parent_node: &NodeRef,
    child_node: &NodeRef,
    parent_relation_field: &RelationFieldRef,
    expected_disconnects: usize,
) -> QueryGraphBuilderResult<NodeRef> {
    assert!(parent_relation_field.relation().is_many_to_many());
    let parent_model_id = parent_relation_field.model().primary_identifier();
    let child_model_id = parent_relation_field.related_model().primary_identifier();

    let disconnect = WriteQuery::DisconnectRecords(DisconnectRecords {
        parent_id: None,
        child_ids: vec![],
        relation_field: Arc::clone(parent_relation_field),
    });

    let disconnect_node = graph.create_node(Query::Write(disconnect));

    // Edge from parent to disconnect.
    graph.create_edge(
        parent_node,
        &disconnect_node,
        QueryGraphDependency::ParentProjection(parent_model_id, Box::new(|mut disconnect_node, mut parent_ids| {
            let parent_id = match parent_ids.pop() {
                Some(pid) => Ok(pid),
                None => Err(QueryGraphBuilderError::AssertionError("[Query Graph] Expected a valid parent ID to be present for a nested disconnect on a many-to-many relation.".to_string())),
            }?;

            if let Node::Query(Query::Write(WriteQuery::DisconnectRecords(ref mut c))) = disconnect_node {
                c.parent_id = Some(parent_id);
            }

            Ok(disconnect_node)
        })),
    )?;

    let relation_name = parent_relation_field.relation().name.clone();
    let parent_name = parent_relation_field.model().name.clone();
    let child_name = parent_relation_field.related_model().name.clone();

    // Edge from child to disconnect.
    graph.create_edge(
        &child_node,
        &disconnect_node,
        QueryGraphDependency::ParentProjection(
            child_model_id,
            Box::new(move |mut disconnect_node, child_ids| {
                let len = child_ids.len();

                if len != expected_disconnects {
                    return Err(QueryGraphBuilderError::RecordsNotConnected {
                        relation_name,
                        parent_name,
                        child_name,
                    });
                }

                if let Node::Query(Query::Write(WriteQuery::DisconnectRecords(ref mut c))) = disconnect_node {
                    c.child_ids = child_ids;
                }

                Ok(disconnect_node)
            }),
        ),
    )?;

    Ok(disconnect_node)
}
