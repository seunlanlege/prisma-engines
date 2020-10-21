#[macro_use]
extern crate tracing;
use query_engine::*;

use cli::CliCommand;
use error::PrismaError;
use opt::PrismaOpt;
use request_handlers::PrismaResponse;
use std::{error::Error, process};
use structopt::StructOpt;



#[cfg(test)]
mod tests;

#[derive(Debug, Clone, PartialEq, Copy)]
pub enum LogFormat {
    Text,
    Json,
}

pub type PrismaResult<T> = Result<T, PrismaError>;
type AnyError = Box<dyn Error + Send + Sync + 'static>;

#[async_std::main]
async fn main() -> Result<(), AnyError> {
    return main().await.map_err(|err| {
        info!("Encountered error during initialization:");
        err.render_as_json().expect("error rendering");
        process::exit(1)
    });

    async fn main() -> Result<(), PrismaError> {
        let opts = PrismaOpt::from_args();
        init_logger(opts.log_format());
        feature_flags::initialize(opts.raw_feature_flags.as_slice())?;
        match CliCommand::from_opt(&opts)? {
            Some(cmd) => cmd.execute().await?,
            None => {
                set_panic_hook(opts.log_format());
                server::listen(opts).await?;
            }
        }
        Ok(())
    }
}


