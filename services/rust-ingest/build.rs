fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("cargo:rerun-if-changed=src/proto/telemetry.proto");
    prost_build::compile_protos(
        &["src/proto/telemetry.proto"],
        &["src/proto"],
    )?;
    Ok(())
}
