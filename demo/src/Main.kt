package demo

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
  // Java messages & Kotlin DSL builders
  val req = helloRequest { name = "Kotlin Toolchain" }
  check(req == HelloRequest.parseFrom(req.toByteArray())) { "Request parsing failed" }

  // grpc-java service stubs.
  val service = GreeterGrpc.getServiceDescriptor()
  check(service.name == "demo.Greeter") { "Unexpected service: ${service.name}" }
  check(service.methods.map { it.bareMethodName } == ["SayHello"]) { "Unexpected methods" }

  // grpc-kotlin coroutine stubs
  val reply = GreeterService().sayHello(req)

  println("Request : ${req.name}")
  println("Reply   : ${reply.message}")
  println(
      "Service : ${service.name} (${service.methods.joinToString { it.bareMethodName.orEmpty() }})"
  )
  println("All generators verified.")
}

/** Implementing a service the grpc-kotlin way */
class GreeterService : GreeterGrpcKt.GreeterCoroutineImplBase() {
  override suspend fun sayHello(request: HelloRequest) = helloReply {
    message = "Hello, ${request.name}!"
  }
}
