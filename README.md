<p align="center">
  <img src="docs/banner.svg" alt="Protobuf Toolchain Plugin" width="100%"/>
</p>

# Protobuf Toolchain Plugin

A local build plugin for the [Kotlin Toolchain](https://kotlin-toolchain.org) that generates Java and Kotlin code from
`.proto` files, with no native `protoc` binary anywhere in sight. It
uses [protobuf4j](https://github.com/roastedroot/protobuf4j) (protoc compiled to WASM, executed as JVM bytecode
by [Endive](https://github.com/bytecodealliance/endive)) and [grpc-kotlin](https://github.com/grpc/grpc-kotlin) for
coroutine stubs. Everything runs on the JVM, so builds behave identically on macOS, Linux, and Windows.

## Quick start

### 1. Register the plugin (`project.yaml`)

```yaml
modules:
  - app
  - protobuf-plugin

plugins:
  - ./protobuf-plugin
```

### 2. Enable it in your module (`app/module.yaml`)

```yaml
product: jvm/app

plugins:
  protobuf-plugin:
    enabled: true
    target: GRPC_KOTLIN
    version: V4

dependencies:
  - com.google.protobuf:protobuf-java:4.35.1
  - com.google.protobuf:protobuf-kotlin:4.35.1
  - io.grpc:grpc-protobuf:1.83.1
  - io.grpc:grpc-stub:1.83.1
  - io.grpc:grpc-kotlin-stub:1.5.0
```

### 3. Drop `.proto` files into `app/src/proto/`

```protobuf
syntax = "proto3";

package demo;

option java_package = "demo";
option java_multiple_files = true;

service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
}

message HelloRequest {string name = 1;}
message HelloReply   {string message = 1;}
```

Subdirectories work: a file is addressed by its path relative to `src/proto`, which is exactly what you write in
`import` statements.

### 4. Build and run

```shell
./kotlin build                                # generates + build
./kotlin run -m app                           # runs the app
```

Generated sources are registered with the compiler automatically: there is nothing to add to `src/`, and nothing
generated is written into your source tree.

## Settings

Both settings are optional.

### `target`

`JAVA` · `KOTLIN` · `GRPC_JAVA` · `GRPC_KOTLIN` &nbsp;&nbsp;→&nbsp;&nbsp; default `KOTLIN`

What the plugin generates. Java messages are always generated and every other generator builds on them, so pick the
highest target you need:

- **`JAVA`** &nbsp;·&nbsp; Java message classes &nbsp;·&nbsp; needs `protobuf-java`
- **`KOTLIN`** &nbsp;·&nbsp; the above plus Kotlin DSL builders &nbsp;·&nbsp; adds `protobuf-kotlin`
- **`GRPC_JAVA`** &nbsp;·&nbsp; `JAVA` plus Java gRPC service stubs &nbsp;·&nbsp; adds `grpc-protobuf`, `grpc-stub`
- **`GRPC_KOTLIN`** &nbsp;·&nbsp; everything, plus Kotlin coroutine gRPC stubs &nbsp;·&nbsp; adds `grpc-kotlin-stub`

### `version`

`V3` · `V4` &nbsp;&nbsp;→&nbsp;&nbsp; default `V4`

The protobuf version.

## How it works

Every generator speaks the protoc plugin protocol (one `CodeGeneratorRequest` in, one `CodeGeneratorResponse` out), so
the task is a straight line:

```
src/proto/**.proto
     │  staged into an in-memory filesystem (the protoc sandbox)
     ▼
CodeGeneratorRequest   (descriptors + transitive imports)
     │
     ├──▶ protoc --java_out     → Java message classes      ─┐
     ├──▶ protoc --kotlin_out   → Kotlin DSL builders        ├──▶ CodeGeneratorResponse
     ├──▶ protoc grpc-java      → Java gRPC stubs            │      → written to the task
     └──▶ grpc-kotlin generator → Kotlin coroutine stubs    ─┘        output directories
```

The order matters: the messages come first, and the DSL builders and stubs are generated on top of them.

## License

Apache-2.0
