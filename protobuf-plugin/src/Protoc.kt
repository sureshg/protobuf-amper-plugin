import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import io.grpc.kotlin.generator.GeneratorRunner
import io.roastedroot.protobuf4j.common.Protobuf.*
import io.roastedroot.protobuf4j.v3.Protobuf as ProtobufV3
import io.roastedroot.protobuf4j.v4.Protobuf as ProtobufV4
import io.roastedroot.zerofs.Configuration
import io.roastedroot.zerofs.ZeroFs
import java.io.ByteArrayOutputStream
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.io.path.*
import org.jetbrains.amper.plugins.*

/** Compiles the module's `.proto` sources into Java and Kotlin code */
@TaskAction
@OptIn(ExperimentalPathApi::class, ExperimentalContextParameters::class)
fun protoc(
    @Input protoDir: Path,
    @Output javaDir: Path,
    @Output kotlinDir: Path,
    target: Target,
    version: ProtobufVersion,
) {
  [javaDir, kotlinDir].forEach { dir ->
    dir.deleteRecursively()
    dir.createDirectories()
  }

  val protos =
      protoDir
          .walk()
          .filter { it.extension == "proto" }
          .map { it.relativeTo(protoDir).invariantSeparatorsPathString }
          .sorted()
          .toList()

  when {
    protos.isEmpty() -> println("No .proto files under $protoDir, nothing to generate.")
    else ->
        sandbox(protoDir, protos).use { fs ->
          println("Compiling ${protos.size} proto file(s): ${protos.joinToString()}")
          val workdir = fs.getPath(".")
          val req = codeGenRequest(version, workdir, protos)

          context(version, workdir, req) {
            when (target) {
              JAVA -> runProtoc(JAVA, javaDir)
              KOTLIN -> {
                runProtoc(JAVA, javaDir)
                runProtoc(KOTLIN, kotlinDir)
              }

              GRPC_JAVA -> {
                runProtoc(JAVA, javaDir)
                runProtoc(GRPC_JAVA, javaDir)
              }

              GRPC_KOTLIN -> {
                runProtoc(JAVA, javaDir)
                runProtoc(KOTLIN, kotlinDir)
                runProtoc(GRPC_JAVA, javaDir)
                runGrpcKotlin(kotlinDir)
              }
            }
          }
        }
  }
}

/** Runs a generator bundled inside protoc's own WASM module and writes its output into [into]. */
context(version: ProtobufVersion, workdir: Path, request: CodeGeneratorRequest)
private fun runProtoc(generator: NativePlugin, into: Path) {
  println("Running ${generator.value()}")
  when (version) {
    V3 -> ProtobufV3.runNativePlugin(generator, request, workdir)
    V4 -> ProtobufV4.runNativePlugin(generator, request, workdir)
  }.writeTo(into)
}

/** grpc-kotlin is a plain JVM generator. */
context(request: CodeGeneratorRequest)
private fun runGrpcKotlin(into: Path) {
  println("Running grpc-kotlin")
  val stdout = ByteArrayOutputStream()
  GeneratorRunner.mainAsProtocPlugin(request.toByteArray().inputStream(), stdout)
  CodeGeneratorResponse.parseFrom(stdout.toByteArray()).writeTo(into)
}

/**
 * Copies the sources into an in-memory filesystem mounts into protoc's WASM sandbox. Generated code
 * comes back as bytes and [writeTo] puts it on the real filesystem.
 */
private fun sandbox(protoDir: Path, protos: List<String>): FileSystem =
    ZeroFs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("unix").build()).apply {
      protos.forEach { proto ->
        protoDir.resolve(proto).copyTo(getPath(".", proto).createParentDirectories())
      }
    }

/** Parses the sources into descriptors, with transitive imports */
private fun codeGenRequest(
    version: ProtobufVersion,
    workdir: Path,
    protos: List<String>,
): CodeGeneratorRequest {
  val descriptors =
      when (version) {
        V3 -> ProtobufV3.builder().withWorkdir(workdir).build().use { it.getDescriptors(protos) }
        V4 -> ProtobufV4.builder().withWorkdir(workdir).build().use { it.getDescriptors(protos) }
      }
  val withImports = FileDescriptorSet.newBuilder()
  val visited: MutableSet<String> = []
  buildFileDescriptors(descriptors).forEach { collectDependencies(it, withImports, visited) }

  return CodeGeneratorRequest.newBuilder()
      .addAllFileToGenerate(protos)
      .addAllProtoFile(withImports.build().fileList)
      .build()
}

private fun CodeGeneratorResponse.writeTo(outputDir: Path) = fileList.forEach { file ->
  outputDir.resolve(file.name).createParentDirectories().writeText(file.content)
  println("  → ${file.name}")
}
