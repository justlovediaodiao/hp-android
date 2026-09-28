package com.hp.vpn.config

import android.content.Context
import java.net.URI
import java.security.cert.CertificateFactory
import java.io.ByteArrayInputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val server: String,
    val password: String,
    val cert: String,
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

enum class RouteMode { GLOBAL, INCLUDE, EXCLUDE }

data class AppConfig(
    val profiles: List<Profile> = emptyList(),
    val selectedId: String? = null,
    val mode: RouteMode = RouteMode.GLOBAL,
    val packages: Set<String> = emptySet(),
)

object ProfileParser {
    fun parseClipboard(raw: String): Profile {
        require(raw.toByteArray(Charsets.UTF_8).size <= 1024 * 1024) { "JSON exceeds 1 MiB" }
        val json = JSONObject(raw)
        require(json.keys().asSequence().all { it in setOf("server", "password", "cert") }) { "JSON contains an unknown field" }
        require(json.has("server") && json.get("server") is String) { "server must be a string" }
        require(json.has("password") && json.get("password") is String) { "password must be a string" }
        require(!json.has("cert") || json.get("cert") is String) { "cert must be a string" }
        val server = json.getString("server").trim()
        validateServer(server)
        val password = json.getString("password")
        require(password.isNotEmpty()) { "password must not be empty" }
        val cert = json.optString("cert", "")
        validateCert(cert)
        return Profile(name = server, server = server, password = password, cert = cert)
    }

    fun validateCert(cert: String) {
        if (cert.isNotBlank()) {
            require(cert.contains("-----BEGIN CERTIFICATE-----") && cert.contains("-----END CERTIFICATE-----")) { "cert is not a PEM certificate" }
            try {
                val parsed = CertificateFactory.getInstance("X.509").generateCertificates(ByteArrayInputStream(cert.toByteArray()))
                require(parsed.isNotEmpty())
            } catch (_: Exception) { throw IllegalArgumentException("cert is not a valid X.509 PEM certificate") }
        }
    }

    fun validateServer(server: String) {
        val uri = try { URI("tcp://$server") } catch (_: Exception) { null }
        require(uri?.host != null && uri.port in 1..65535 && uri.rawUserInfo == null &&
            uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null) {
            "server must be host:port; enclose IPv6 addresses in brackets"
        }
    }
}

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    @Synchronized fun read(): AppConfig {
        val raw = prefs.getString("data", null) ?: return AppConfig()
        return fromJson(JSONObject(raw))
    }

    @Synchronized fun write(config: AppConfig) {
        prefs.edit().putString("data", toJson(config).toString()).apply()
    }

    private fun toJson(c: AppConfig) = JSONObject().apply {
        put("selectedId", c.selectedId)
        put("mode", c.mode.name)
        put("packages", JSONArray(c.packages.toList()))
        put("profiles", JSONArray().apply { c.profiles.forEach { p -> put(JSONObject().apply {
            put("id", p.id); put("name", p.name); put("server", p.server)
            put("password", p.password); put("cert", p.cert)
        }) } })
    }

    private fun fromJson(o: JSONObject): AppConfig {
        val list = o.optJSONArray("profiles") ?: JSONArray()
        val profiles = (0 until list.length()).map { i -> list.getJSONObject(i).let { p ->
            Profile(p.getString("id"), p.getString("name"), p.getString("server"),
                p.getString("password"), p.optString("cert", ""))
        } }
        val pkgs = o.optJSONArray("packages") ?: JSONArray()
        return AppConfig(profiles, if (o.isNull("selectedId")) null else o.getString("selectedId"),
            RouteMode.valueOf(o.optString("mode", "GLOBAL")),
            (0 until pkgs.length()).map { pkgs.getString(it) }.toSet())
    }
}
