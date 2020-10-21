use enumflags2::BitFlags;
use std::{error::Error, fmt::Display, str::FromStr};

#[derive(BitFlags, Copy, Clone, Debug, PartialEq)]
#[repr(u8)]
pub enum Capabilities {
    ScalarLists = 0b0001,
    Enums = 0b0010,
    Json = 0b0100,
}

#[derive(Debug)]
pub struct UnknownCapabilityError(String);

impl Display for UnknownCapabilityError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let available_capability_names: Vec<&str> = CAPABILITY_NAMES.iter().map(|(name, _)| *name).collect();

        write!(
            f,
            "Unknown capability `{}`. Available capabilities: {:?}",
            self.0, available_capability_names
        )
    }
}

impl Error for UnknownCapabilityError {}

impl FromStr for Capabilities {
    type Err = UnknownCapabilityError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        CAPABILITY_NAMES
            .binary_search_by_key(&s, |(name, _capability)| *name)
            .ok()
            .and_then(|idx| CAPABILITY_NAMES.get(idx))
            .map(|(_name, capability)| *capability)
            .ok_or_else(|| UnknownCapabilityError(s.to_owned()))
    }
}

/// All the capabilities, sorted by name.
const CAPABILITY_NAMES: &[(&str, Capabilities)] = &[
    ("enums", Capabilities::Enums),
    ("json", Capabilities::Json),
    ("scalar_lists", Capabilities::ScalarLists),
];
