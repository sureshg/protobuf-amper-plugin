import org.jetbrains.amper.plugins.Configurable

/** Which protobuf4j WASM module to use. Must match your app's protobuf-java version. */
enum class ProtobufVersion { V3, V4 }

/**
 * Code generation mode. Each mode implies the generators it depends on:
 *  - JAVA        → java message classes
 *  - KOTLIN      → java + kotlin DSL wrappers
 *  - GRPC_JAVA   → java + grpc-java service stubs
 *  - GRPC_KOTLIN → java + kotlin + grpc-java + grpc-kotlin stubs
 */
enum class ProtoPlugin { JAVA, KOTLIN, GRPC_JAVA, GRPC_KOTLIN }

@Configurable
interface Schema {
    /** protobuf4j version: V3 (protobuf-java 3.25.x) or V4 (protobuf-java 4.28.x). */
    val protobufVersion: ProtobufVersion get() = ProtobufVersion.V4

    /** Code generation mode. Higher modes automatically include the generators they depend on. */
    val plugin: ProtoPlugin get() = ProtoPlugin.KOTLIN
}
