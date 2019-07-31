package com.hexagonkt.http.client

import com.hexagonkt.helpers.require
import com.hexagonkt.http.server.Call
import com.hexagonkt.http.server.Server
import com.hexagonkt.http.server.jetty.JettyServletAdapter
import com.hexagonkt.serialization.Json
import com.hexagonkt.serialization.serialize

import org.asynchttpclient.Response
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File

@Test
class ClientTest {

    private var handler: Call.() -> Unit = {}

    private val server: Server by lazy {
        Server(JettyServletAdapter()) {
            post("/*") { handler() }
            get("/*") { handler() }
            head("/*") { handler() }
            put("/*") { handler() }
            delete("/*") { handler() }
            trace("/*") { handler() }
            options("/*") { handler() }
            patch("/*") { handler() }
        }
    }

    private val client by lazy {
        Client("http://localhost:${server.runtimePort}", Json.contentType)
    }

    @BeforeClass
    fun startup() {
        server.start()
    }

    @AfterClass
    fun shutdown() {
        server.stop()
    }

    @BeforeMethod
    fun resetHandler() {
        handler = {
            response.headers["content-type"] = listOf("application/json;charset=utf-8")
            response.headers["body"] = listOf(request.body)
            ok(request.body)
        }
    }

    fun `JSON requests works as expected`() {
        val expectedBody = "{\n  \"foo\" : \"fighters\",\n  \"es\" : \"áéíóúÁÉÍÓÚñÑ\"\n}"
        val requestBody = mapOf("foo" to "fighters", "es" to "áéíóúÁÉÍÓÚñÑ")

        val body = client.post("/", requestBody, Json.contentType).responseBody
        assert(body.trim() == expectedBody)

        val body2 = client.post("/", body = requestBody).responseBody
        assert(body2.trim() == expectedBody)

        client.get("/")
        client.get("/")
    }

    fun `HTTP methods with objects work ok`() {
        val parameter = mapOf("key" to "value")
        checkResponse(client.get("/"), null)
        checkResponse(client.head("/"), null)
        checkResponse(client.post("/", parameter), parameter)
        checkResponse(client.put("/", parameter), parameter)
        checkResponse(client.delete("/", parameter), parameter)
        checkResponse(client.trace("/", parameter), parameter)
        checkResponse(client.options("/", parameter), parameter)
        checkResponse(client.patch("/", parameter), parameter)
    }

    fun `HTTP methods with objects work ok with default client`() {
        val parameter = mapOf("key" to "value")
        val url = "http://localhost:${server.runtimePort}"
        val contentType = Json.contentType
        checkResponse(get(url), null)
        checkResponse(head(url), null)
        checkResponse(post(url), null)
        checkResponse(put(url), null)
        checkResponse(delete(url), null)
        checkResponse(trace(url), null)
        checkResponse(options(url), null)
        checkResponse(patch(url), null)
        checkResponse(post(url, parameter), parameter.toString())
        checkResponse(put(url, parameter), parameter.toString())
        checkResponse(delete(url, parameter), parameter.toString())
        checkResponse(trace(url, parameter), parameter.toString())
        checkResponse(options(url, parameter), parameter.toString())
        checkResponse(patch(url, parameter), parameter.toString())
        checkResponse(post(url, parameter, contentType), parameter)
        checkResponse(put(url, parameter, contentType), parameter)
        checkResponse(delete(url, parameter, contentType), parameter)
        checkResponse(trace(url, parameter, contentType), parameter)
        checkResponse(options(url, parameter, contentType), parameter)
        checkResponse(patch(url, parameter, contentType), parameter)
    }

    fun `Parameters are set properly` () {
        val endpoint = "http://localhost:${server.runtimePort}"
        val h = mapOf("header1" to listOf("val1", "val2"))
        val c = Client(endpoint, Json.contentType, false, h, "user", "password", true)

        assert(c.contentType == Json.contentType)
        assert(!c.useCookies)
        assert(c.headers == h)

        handler = {
            response.headers["auth"] = listOf(request.headers.require("Authorization").first())
            response.headers["head1"] = request.headers.require("header1")
        }

        val r = c.get("/auth")
        assert (r.headers.get("auth").startsWith("Basic"))
        assert (r.headers.getAll("head1").contains("val1"))
        assert (r.headers.getAll("head1").contains("val2"))
        assert (r.statusCode == 200)
    }

    fun `Files are sent in base64` () {
        handler = { response.headers["file64"] = listOf(request.body) }

        val file = File("src/test/resources/logback-test.xml").let {
            if (it.exists()) it
            else File("port_http_client/src/test/resources/logback-test.xml")
        }

        val r = client.post("/file", file)
        assert (r.headers.get("file64").isNotEmpty())
        assert (r.statusCode == 200)
    }

    fun `Strings are sent properly` () {
        val r = client.post("/string", "text")
        assert (r.headers.get("body").isNotEmpty())
        assert (r.statusCode == 200)
    }

    private fun checkResponse(response: Response, parameter: Map<String, String>?) {
        assert(response.statusCode == 200)
        assert(response.responseBody.trim() == parameter?.serialize()?.trim() ?: "")
    }

    private fun checkResponse(response: Response, parameter: String) {
        assert(response.statusCode == 200)
        assert(response.responseBody.trim() == parameter?.trim())
    }
}
