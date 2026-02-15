import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import io.roastedroot.protobuf4j.common.Protobuf.NativePlugin
import io.roastedroot.protobuf4j.common.Protobuf.buildFileDescriptors
import io.roastedroot.protobuf4j.common.Protobuf.collectDependencies
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.*
import io.grpc.kotlin.generator.GeneratorRunner

import io.roastedroot.protobuf4j.v3.Protobuf as ProtobufV3
import io.roastedroot.protobuf4j.v4.Protobuf as ProtobufV4

@TaskAction
@OptIn(ExperimentalPathApi::class)
fun generateProto(
    @Input protoDir: Path,
    @Output javaOutputDir: Path,
    @Output kotlinOutputDir: Path,
    plugin: ProtoPlugin,
    protobufVersion: ProtobufVersion,
) {
    javaOutputDir.deleteRecursively(); javaOutputDir.createDirectories()
    kotlinOutputDir.deleteRecursively(); kotlinOutputDir.createDirectories()

    val protoFiles = protoDir.walk()
        .filter { it.extension == "proto" }
        .map { it.name }
        .toList()

    if (protoFiles.isEmpty()) {
        println("No .proto files found in $protoDir — skipping.")
        return
    }
    println("Compiling ${protoFiles.size} proto file(s): $protoFiles")

    // Parse .proto → descriptors (needs a short-lived protobuf4j instance)
    val descriptorSet = when (protobufVersion) {
        ProtobufVersion.V3 -> ProtobufV3.builder().withWorkdir(protoDir).build().use { it.getDescriptors(protoFiles) }
        ProtobufVersion.V4 -> ProtobufV4.builder().withWorkdir(protoDir).build().use { it.getDescriptors(protoFiles) }
    }

    val request = buildCodeGeneratorRequest(descriptorSet, protoFiles)

    // Helper: run a protobuf4j native plugin (static method, creates its own WASM instance)
    fun runNative(nativePlugin: NativePlugin): CodeGeneratorResponse = when (protobufVersion) {
        ProtobufVersion.V3 -> ProtobufV3.runNativePlugin(nativePlugin, request, protoDir)
        ProtobufVersion.V4 -> ProtobufV4.runNativePlugin(nativePlugin, request, protoDir)
    }

    // --java_out: always generated (everything else depends on it)
    println("Running: --java_out")
    runNative(NativePlugin.JAVA).writeTo(javaOutputDir)

    // --kotlin_out: Kotlin DSL message wrappers
    if (plugin == ProtoPlugin.KOTLIN || plugin == ProtoPlugin.GRPC_KOTLIN) {
        println("Running: --kotlin_out")
        runNative(NativePlugin.KOTLIN).writeTo(kotlinOutputDir)
    }

    // grpc-java: Java gRPC service stubs (WASM via protobuf4j)
    if (plugin == ProtoPlugin.GRPC_JAVA || plugin == ProtoPlugin.GRPC_KOTLIN) {
        println("Running: grpc-java")
        runNative(NativePlugin.GRPC_JAVA).writeTo(javaOutputDir)
    }

    // grpc-kotlin: Kotlin coroutine gRPC stubs (pure JVM, no native binary)
    if (plugin == ProtoPlugin.GRPC_KOTLIN) {
        println("Running: grpc-kotlin")
        val output = ByteArrayOutputStream()
        GeneratorRunner.mainAsProtocPlugin(ByteArrayInputStream(request.toByteArray()), output)
        CodeGeneratorResponse.parseFrom(output.toByteArray()).writeTo(kotlinOutputDir)
    }

    println("Protobuf code generation complete.")
}

private fun buildCodeGeneratorRequest(
    descriptorSet: FileDescriptorSet,
    protoFiles: List<String>,
): CodeGeneratorRequest {
    val allDescriptors = FileDescriptorSet.newBuilder()
    val added = mutableSetOf<String>()
    for (fd in buildFileDescriptors(descriptorSet)) {
        collectDependencies(fd, allDescriptors, added)
    }
    return CodeGeneratorRequest.newBuilder()
        .addAllFileToGenerate(protoFiles)
        .addAllProtoFile(allDescriptors.build().fileList)
        .build()
}

private fun CodeGeneratorResponse.writeTo(outputDir: Path) {
    for (file in fileList) {
        val target = outputDir.resolve(file.name)
        target.parent.createDirectories()
        target.writeText(file.content)
        println("  → ${file.name}")
    }
}
