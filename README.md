<p align="center">
  <img src="docs/banner.png" alt="Amper Protobuf Plugin" width="100%"/>
</p>

# Amper Protobuf Plugin

A build plugin for [Amper](https://amper.org) that generates Java/Kotlin code from `.proto` files — no native `protoc` binary needed.

It uses [protobuf4j](https://github.com/roastedroot/protobuf4j) (protoc compiled to WASM, running as pure JVM bytecode via [Chicory](https://github.com/nicolarevelant/chicory)) and [grpc-kotlin](https://github.com/grpc/grpc-kotlin) for coroutine-based stubs. Everything runs on the JVM, so it works the same on macOS, Linux, and Windows without any platform-specific binaries.

## How it works

```
.proto files
     │
     ▼
 protobuf4j  (protoc → WASM → JVM bytecode)
     │
     ├──▶ NativePlugin.JAVA         → Java message classes
     ├──▶ NativePlugin.KOTLIN       → Kotlin DSL message wrappers
     ├──▶ NativePlugin.GRPC_JAVA    → Java gRPC service stubs
     │
     └──▶ GeneratorRunner            → Kotlin gRPC coroutine stubs (pure JVM)
           io.grpc:protoc-gen-grpc-kotlin
```

No `protoc` binary. No `protoc-gen-grpc-java` binary. Just the JVM.

## Quick start

### Project layout

```
my-project/
├── protobuf-plugin/          # the plugin itself
│   ├── module.yaml
│   ├── plugin.yaml
│   └── src/
│       ├── Schema.kt
│       └── GenerateProto.kt
├── my-app/
│   ├── module.yaml
│   └── src/
│       └── proto/
│           └── hello.proto
└── project.yaml
```

### 1. Register the plugin (`project.yaml`)

```yaml
modules:
  - protobuf-plugin
  - my-app

plugins:
  - ./protobuf-plugin
```

### 2. Configure your module (`my-app/module.yaml`)

```yaml
product: jvm/app

settings:
  jvm:
    mainClass: MainKt

plugins:
  protobuf-plugin:
    enabled: true
    plugin: GRPC_KOTLIN

dependencies:
  - $libs.protobuf.java
  - $libs.protobuf.kotlin
  - $libs.grpc.protobuf
  - $libs.grpc.stub
  - $libs.grpc.kotlin.stub
```

### 3. Drop `.proto` files in `src/proto/`

```protobuf
syntax = "proto3";

option java_package = "com.example.grpc";
option java_multiple_files = true;

service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
}

message HelloRequest { string name = 1; }
message HelloReply   { string message = 1; }
```

### 4. Build

```shell
./amper build
```

Generated Java sources go to `java-sources`, Kotlin sources to `kotlin-sources` — both get picked up by the compiler automatically.

## Plugin settings

| Setting           | Values            | Default  | Description                         |
|-------------------|-------------------|----------|-------------------------------------|
| `protobufVersion` | `V3` / `V4`      | `V4`     | Which protobuf4j WASM module to use |
| `plugin`          | see table below   | `KOTLIN` | What code to generate               |

### Plugin modes

Each mode includes the generators it depends on, so you only pick the highest level you need:

| Mode          | Generates                                                  |
|---------------|------------------------------------------------------------|
| `JAVA`        | Java message classes                                       |
| `KOTLIN`      | above + Kotlin DSL wrappers                                |
| `GRPC_JAVA`   | `JAVA` + Java gRPC service stubs                           |
| `GRPC_KOTLIN` | everything — Java, Kotlin, gRPC Java stubs, gRPC Kotlin stubs |

### Runtime dependencies

What you need in your module's `dependencies` depends on the mode:

| Mode          | Dependencies                                                              |
|---------------|---------------------------------------------------------------------------|
| `JAVA`        | `protobuf-java`                                                           |
| `KOTLIN`      | `protobuf-java`, `protobuf-kotlin`                                        |
| `GRPC_JAVA`   | `protobuf-java`, `grpc-protobuf`, `grpc-stub`                             |
| `GRPC_KOTLIN` | all of the above + `grpc-kotlin-stub`                                      |

If you're running an actual gRPC server or client, you'll also want a transport like `io.grpc:grpc-netty-shaded` or `io.grpc:grpc-okhttp`.

### Protobuf version

| Value | protobuf4j artifact | Use when                          |
|-------|---------------------|-----------------------------------|
| `V3`  | `protobuf4j-v3`     | your app uses `protobuf-java` 3.x |
| `V4`  | `protobuf4j-v4`     | your app uses `protobuf-java` 4.x |

## License

Apache-2.0