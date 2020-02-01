package com.phmagic89

import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(Authentication) {
        basic("myBasicAuth") {
            realm = "Ktor Server"
            validate {
                if (it.name == "user" && it.password == "password") {
                    UserPrincipal(it.name, UserType.USER)
                } else if (it.name == "admin" && it.password == "password") {
                    UserPrincipal(it.name, UserType.ADMIN)
                } else null
            }
        }
    }

    routing {
        authenticate("myBasicAuth") {
            authorize(UserType.ADMIN) {
                get("/admin") {
                    val principal = call.principal<UserPrincipal>()!!
                    call.respondText("Hello ${principal.username}. That's page with only admin access")
                }
            }
            authorize(UserType.USER) {
                get("/user") {
                    val principal = call.principal<UserPrincipal>()!!
                    call.respondText("Hello ${principal.username}. That's page with only user access")
                }
            }
        }
    }
}

enum class UserType { ADMIN, USER }
data class UserPrincipal(val username: String, val type: UserType) : Principal

fun Route.authorize(type: UserType, callback: Route.() -> Unit): Route {
    val authorizeRoute = this.createChild(object : RouteSelector(1.0) {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
            RouteSelectorEvaluation.Constant
    })
    authorizeRoute.intercept(ApplicationCallPipeline.Call) {
        val principal = this.call.principal<UserPrincipal>()
        if (principal != null && principal.type == type) {
            proceed()
        } else {
            this.call.respond(HttpStatusCode.Forbidden, "Forbidden")
            finish()
        }
    }
    callback(authorizeRoute)
    return authorizeRoute
}
