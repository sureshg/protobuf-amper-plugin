package com.example

import examples.GreeterGrpc
import examples.helloRequest
import examples.helloReply

fun main() {
    // Kotlin DSL message builders (from --kotlin_out)
    val request = helloRequest { name = "Amper" }
    val response = helloReply { message = "Hello, ${request.name}!" }

    println("Request : ${request.name}")
    println("Response: ${response.message}")

    // gRPC service descriptor (from grpc-java)
    val descriptor = GreeterGrpc.getServiceDescriptor()
    println("Service : ${descriptor.name}")
    println("Methods : ${descriptor.methods.joinToString { it.bareMethodName ?: it.fullMethodName }}")
}
