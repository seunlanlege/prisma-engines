use tide::{Middleware, Next, Request};

use std::time::Instant;

/// Middleware to set the `X-Elapsed` header.
#[derive(Debug, Clone)]
pub(crate) struct ElapsedMiddleware {
    _priv: (),
}

impl ElapsedMiddleware {
    /// Creates a new `ElapsedMiddleware`.
    pub fn new() -> Self {
        Self { _priv: () }
    }
}

#[tide::utils::async_trait]
impl<State: Clone + Send + Sync + 'static> Middleware<State> for ElapsedMiddleware {
    async fn handle(&self, req: Request<State>, next: Next<'_, State>) -> tide::Result {
        let start = Instant::now();
        let mut res = next.run(req).await;
        let elapsed = Instant::now().duration_since(start).as_micros() as u64;
        res.insert_header("x-elapsed", format!("{}", elapsed));
        Ok(res)
    }
}
