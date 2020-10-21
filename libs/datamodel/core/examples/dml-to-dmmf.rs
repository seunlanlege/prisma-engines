use std::fs;

extern crate clap;
use clap::{App, Arg};

fn main() {
    let matches = App::new("Prisma Datamodel v2 to DMMF")
        .version("0.1")
        .author("Emanuel Jöbstl <emanuel.joebstl@gmail.com>")
        .about("Converts a datamodel v2 file to the DMMF JSON representation.")
        .arg(
            Arg::with_name("INPUT")
                .help("Sets the input datamodel file to use")
                .required(true)
                .index(1),
        )
        .get_matches();

    let file_name = matches.value_of("INPUT").unwrap();
    let file = fs::read_to_string(&file_name).unwrap_or_else(|_| panic!("Unable to open file {}", file_name));

    let validated = datamodel::parse_datamodel_or_pretty_error(&file, &file_name);

    match &validated {
        Err(formatted) => {
            println!("{}", formatted);
        }
        Ok(dml) => {
            let json = datamodel::json::dmmf::render_to_dmmf(&dml.subject);
            println!("{}", json);
        }
    }
}
