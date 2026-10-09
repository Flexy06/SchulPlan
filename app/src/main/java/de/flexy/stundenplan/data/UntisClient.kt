package de.flexy.stundenplan.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Base64

/** Zugangsdaten für WebUntis. [server] z.B. "csska.webuntis.com", [school] z.B. "csska". */
data class UntisAccount(val server: String, val school: String, val user: String, val password: String) {
    companion object {
        const val DEFAULT_SERVER = "csska.webuntis.com"
        const val DEFAULT_SCHOOL = "csska"
        const val DEFAULT_SCHOOL_NAME = "Carlo-Schmid-Schule Karlsruhe"

        /** Liest Server und Schule aus einem WebUntis-Link wie https://csska.webuntis.com/WebUntis/?school=csska#/… */
        fun parseLink(link: String): Pair<String, String>? {
            val m = Regex("""^(?:https?://)?([^/?#\s]+)/.*[?&]school=([^&#\s]+)""").find(link.trim()) ?: return null
            return m.groupValues[1] to java.net.URLDecoder.decode(m.groupValues[2], "UTF-8")
        }
    }
}

class UntisException(message: String, val badCredentials: Boolean = false) : IOException(message)

/**
 * Minimaler WebUntis-Client: JSON-RPC (Login, Stundenplan, Klassen) und die
 * REST-Schnittstellen für Hausaufgaben und Prüfungen, die auch die Weboberfläche nutzt.
 */
class UntisClient(private val account: UntisAccount) {

    data class Session(val id: String, val personType: Int, val personId: Int, val klasseId: Int)

    private val base = "https://${account.server.trim().trimEnd('/')}/WebUntis"
    private var session: Session? = null
    private var bearer: String? = null

    fun login(): Session {
        val r = rpc(
            "authenticate",
            JSONObject().put("user", account.user).put("password", account.password).put("client", "SchulPlan-App"),
            authenticated = false,
        ) as? JSONObject ?: throw UntisException("Unerwartete Antwort von WebUntis.")
        val s = Session(
            id = r.optString("sessionId"),
            personType = r.optInt("personType", 5),
            personId = r.optInt("personId", 0),
            klasseId = r.optInt("klasseId", 0),
        )
        if (s.id.isBlank()) throw UntisException("Anmeldung fehlgeschlagen.")
        session = s
        return s
    }

    fun logout() {
        if (session == null) return
        runCatching { rpc("logout", JSONObject()) }
        session = null
        bearer = null
    }

    /** Stundenplan von [from] bis [to] (eigener Plan; ohne Berechtigung ersatzweise der Klassenplan). */
    fun timetable(from: LocalDate, to: LocalDate): JSONArray {
        val s = session ?: login()
        fun query(type: Int, id: Int) = rpc(
            "getTimetable",
            JSONObject().put(
                "options",
                JSONObject()
                    .put("element", JSONObject().put("id", id).put("type", type))
                    .put("startDate", UntisParser.ymd(from))
                    .put("endDate", UntisParser.ymd(to))
                    .put("showInfo", true)
                    .put("showSubstText", true)
                    .put("showLsText", true)
                    .put("klasseFields", JSONArray(listOf("id", "name", "longname")))
                    .put("roomFields", JSONArray(listOf("id", "name", "longname")))
                    .put("subjectFields", JSONArray(listOf("id", "name", "longname")))
                    .put("teacherFields", JSONArray(listOf("id", "name", "longname"))),
            ),
        ) as? JSONArray ?: JSONArray()

        return try {
            query(if (s.personId > 0) s.personType else 1, if (s.personId > 0) s.personId else s.klasseId)
        } catch (e: UntisException) {
            if (s.klasseId > 0 && s.personType != 1) query(1, s.klasseId) else throw e
        }
    }

    /** Name der eigenen Klasse (falls die Schule das erlaubt). */
    fun klasseName(): String? = runCatching {
        val s = session ?: login()
        if (s.klasseId <= 0) return null
        val a = rpc("getKlassen", JSONObject()) as? JSONArray ?: return null
        (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optInt("id") == s.klasseId }?.optString("name")
    }.getOrNull()

    fun homework(from: LocalDate, to: LocalDate): JSONObject =
        JSONObject(rest("/api/homeworks/lessons?startDate=${UntisParser.ymd(from)}&endDate=${UntisParser.ymd(to)}"))

    fun exams(from: LocalDate, to: LocalDate): JSONObject {
        val k = session?.klasseId?.takeIf { it > 0 } ?: -1
        return JSONObject(rest("/api/exams?startDate=${UntisParser.ymd(from)}&endDate=${UntisParser.ymd(to)}&klasseId=$k&withGrades=true"))
    }

    /* ------------------------------------------------------------------------------------------ */

    private fun cookies(): String {
        val sb = StringBuilder()
        session?.let { sb.append("JSESSIONID=").append(it.id).append("; ") }
        val school64 = Base64.getEncoder().encodeToString(account.school.toByteArray(Charsets.UTF_8))
        sb.append("schoolname=\"_").append(school64).append('"')
        return sb.toString()
    }

    private fun rpc(method: String, params: JSONObject, authenticated: Boolean = true): Any? {
        if (authenticated && session == null) login()
        val url = "$base/jsonrpc.do?school=" + URLEncoder.encode(account.school, "UTF-8")
        val body = JSONObject()
            .put("id", System.currentTimeMillis().toString())
            .put("method", method)
            .put("params", params)
            .put("jsonrpc", "2.0")
            .toString()
        val text = http(url, "POST", body)
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw UntisException("WebUntis antwortet nicht wie erwartet – Schule/Server prüfen.")
        }
        json.optJSONObject("error")?.let { err ->
            val code = err.optInt("code")
            throw when (code) {
                -8504 -> UntisException("Benutzername oder Passwort falsch.", badCredentials = true)
                -8500 -> UntisException("Schule „${account.school}“ nicht gefunden.")
                -8520 -> UntisException("Sitzung abgelaufen – bitte erneut versuchen.")
                -8509, -8510 -> UntisException("Keine Berechtigung für den Stundenplan.")
                else -> UntisException(err.optString("message", "WebUntis-Fehler $code"))
            }
        }
        return json.opt("result")
    }

    private fun rest(path: String): String {
        if (session == null) login()
        if (bearer == null) {
            bearer = runCatching { http("$base/api/token/new", "GET", null).trim().trim('"') }
                .getOrNull()?.takeIf { it.count { c -> c == '.' } == 2 }
        }
        return http(base + path, "GET", null, bearer)
    }

    private fun http(url: String, method: String, body: String?, token: String? = null): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "SchulPlan-App (Android)")
            setRequestProperty("Cookie", cookies())
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw UntisException("WebUntis antwortet mit Fehler $code.")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
