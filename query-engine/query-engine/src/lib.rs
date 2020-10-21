#[macro_use]
extern crate tracing;

pub mod cli;
pub mod context;
pub mod dmmf;
pub mod error;
pub mod exec_loader;
pub mod opt;
pub mod request_handlers;
pub mod server;

use error::*;
use std::error::Error;
use tracing::subscriber;
use request_handlers::PrismaResponse;
use tracing_subscriber::{EnvFilter, FmtSubscriber};
use tide_server_timing::TimingLayer;
use tracing_subscriber::layer::SubscriberExt;

#[cfg(test)]
mod tests;

#[derive(Debug, Clone, PartialEq, Copy)]
pub enum LogFormat {
	Text,
	Json,
}

pub type PrismaResult<T> = Result<T, PrismaError>;
pub type AnyError = Box<dyn Error + Send + Sync + 'static>;

pub fn init_feature_flags(opts: &opt::PrismaOpt) {
    if let Err(err) = feature_flags::initialize(opts.raw_feature_flags.as_slice()) {
        let err: PrismaError = err.into();

        info!("Encountered error during initialization:");
        err.render_as_json().expect("error rendering");
        std::process::exit(1);
    }
}

pub fn init_logger(log_format: LogFormat) {
    // Enable `tide` logs to be captured.
    let filter = EnvFilter::from_default_env().add_directive("tide=info".parse().unwrap());

    match log_format {
        LogFormat::Text => {
            let subscriber = FmtSubscriber::builder()
                .with_max_level(tracing::Level::TRACE)
                .finish()
                .with(TimingLayer::new());

            subscriber::set_global_default(subscriber).expect("Could not initialize logger");
        }
        LogFormat::Json => {
            let subscriber = FmtSubscriber::builder()
                .json()
                .with_env_filter(filter)
                .finish()
                .with(TimingLayer::new());
            subscriber::set_global_default(subscriber).expect("Could not initialize logger");
        }
    }
}

pub fn set_panic_hook(log_format: LogFormat) {
    if let LogFormat::Json = log_format {
        std::panic::set_hook(Box::new(|info| {
            let payload = info
                .payload()
                .downcast_ref::<String>()
                .map(Clone::clone)
                .unwrap_or_else(|| info.payload().downcast_ref::<&str>().unwrap().to_string());

            match info.location() {
                Some(location) => {
                    tracing::event!(
                        tracing::Level::ERROR,
                        message = "PANIC",
                        reason = payload.as_str(),
                        file = location.file(),
                        line = location.line(),
                        column = location.column(),
                    );
                }
                None => {
                    tracing::event!(tracing::Level::ERROR, message = "PANIC", reason = payload.as_str());
                }
            }

            std::process::exit(255);
        }));
    }
}
