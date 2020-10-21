use std::{borrow::Cow, collections::HashMap};

/// The information about the current state of the database gathered by the destructive change checker.
#[derive(Debug, Default)]
pub(super) struct DatabaseInspectionResults {
    /// HashMap from table name to row count.
    row_counts: HashMap<String, i64>,
    /// HashMap from (table name, column name) to non-null values count.
    value_counts: HashMap<(Cow<'static, str>, Cow<'static, str>), i64>,
}

impl DatabaseInspectionResults {
    pub(super) fn get_row_count(&self, table: &str) -> Option<i64> {
        self.row_counts.get(table).copied()
    }

    pub(super) fn set_row_count(&mut self, table: String, row_count: i64) {
        self.row_counts.insert(table, row_count);
    }

    pub(super) fn get_row_and_non_null_value_count(&self, table: &str, column: &str) -> (Option<i64>, Option<i64>) {
        (
            self.row_counts.get(table).copied(),
            self.value_counts
                .get(&(Cow::Borrowed(table), Cow::Borrowed(column)))
                .copied(),
        )
    }

    pub(super) fn set_value_count(&mut self, table: Cow<'static, str>, column: Cow<'static, str>, count: i64) {
        self.value_counts.insert((table, column), count);
    }
}
