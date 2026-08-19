import org.jetbrains.amper.plugins.Configurable

enum class ProtobufVersion {
  V3,
  V4,
}

/** The codegen target */
enum class Target {
  /** Java message classes. */
  JAVA,

  /** Java messages + Kotlin DSL builders. */
  KOTLIN,

  /** Java messages + Java gRPC service stubs. */
  GRPC_JAVA,

  /**
   * Everything: Java messages, Kotlin DSL builders, Java gRPC stubs, Kotlin coroutine gRPC stubs.
   */
  GRPC_KOTLIN,
}

@Configurable
interface Settings {
  /** Codegen Target */
  val target: Target
    get() = Target.KOTLIN

  /** `V3` for `protobuf-java` 3.x, `V4` for 4.x. */
  val version: ProtobufVersion
    get() = ProtobufVersion.V4
}
