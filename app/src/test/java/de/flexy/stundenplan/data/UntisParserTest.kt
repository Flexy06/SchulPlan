package de.flexy.stundenplan.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class UntisParserTest {

    private val timetable = """
    [
      {"id":101,"date":20261012,"startTime":745,"endTime":830,
       "kl":[{"id":1,"name":"10a"}],"te":[{"id":5,"name":"MÜL","longname":"Müller"}],
       "su":[{"id":7,"name":"M","longname":"Mathematik"}],"ro":[{"id":3,"name":"204","longname":"Raum 204"}]},
      {"id":102,"date":20261012,"startTime":835,"endTime":920,
       "kl":[{"id":1,"name":"10a"}],"te":[{"id":5,"name":"MÜL","longname":"Müller"}],
       "su":[{"id":7,"name":"M","longname":"Mathematik"}],"ro":[{"id":3,"name":"204","longname":"Raum 204"}]},
      {"id":103,"date":20261012,"startTime":940,"endTime":1025,"code":"cancelled",
       "te":[{"id":6,"name":"SCH","longname":"Schmidt"}],
       "su":[{"id":8,"name":"D","longname":"Deutsch"}],"ro":[{"id":4,"name":"105"}],"substText":"Lehrer krank"},
      {"id":104,"date":20261012,"startTime":1030,"endTime":1115,"code":"irregular",
       "te":[{"id":9,"name":"KOC","longname":"Koch","orgid":6,"orgname":"SCH"}],
       "su":[{"id":9,"name":"E","longname":"Englisch"}],"ro":[{"id":5,"name":"110","orgid":4,"orgname":"105"}]},
      {"id":105,"date":20261013,"startTime":800,"endTime":845,
       "te":[],"su":[],"ro":[],"lstext":"Schulversammlung"}
    ]
    """.trimIndent()

    @Test
    fun parsesAndMergesDoubleLessons() {
        val l = UntisParser.timetable(JSONArray(timetable))
        assertEquals(4, l.size)
        val math = l[0]
        assertEquals("Mathematik", math.title)
        assertEquals("M", math.code)
        assertEquals("Müller", math.teacher)
        assertEquals("204", math.location)
        assertEquals(LocalDateTime.of(2026, 10, 12, 7, 45), math.start)
        assertEquals(LocalDateTime.of(2026, 10, 12, 9, 20), math.end)
        assertFalse(math.changed)
    }

    @Test
    fun cancellationAndSubstitution() {
        val l = UntisParser.timetable(JSONArray(timetable))
        val german = l.first { it.title == "Deutsch" }
        assertTrue(german.cancelled)
        assertEquals("Lehrer krank", german.note)
        val english = l.first { it.title == "Englisch" }
        assertTrue(english.changed)
        assertTrue(english.changeInfo, english.changeInfo.contains("Vertretung für SCH"))
        assertTrue(english.changeInfo, english.changeInfo.contains("Raum 110 statt 105"))
        val event = l.first { it.date == LocalDate.of(2026, 10, 13) }
        assertEquals("Schulversammlung", event.title)
    }

    @Test
    fun homework() {
        val json = JSONObject("""
        {"data":{"records":[{"homeworkId":1,"teacherId":5,"elementIds":[1]}],
          "homeworks":[{"id":1,"lessonId":77,"date":20261012,"dueDate":20261015,"text":"S. 42 Nr. 3","remark":"","completed":false}],
          "teachers":[{"id":5,"name":"Müller"}],
          "lessons":[{"id":77,"subject":"M","lessonType":"Unterricht"}]}}
        """.trimIndent())
        val h = UntisParser.homework(json)
        assertEquals(1, h.size)
        assertEquals("M", h[0].subject)
        assertEquals("Müller", h[0].teacher)
        assertEquals(LocalDate.of(2026, 10, 15), h[0].due)
    }

    @Test
    fun exams() {
        val json = JSONObject("""
        {"data":{"exams":[{"id":3,"examType":"Klassenarbeit","name":"KA 1","studentClass":["10a"],
          "examDate":20261020,"startTime":745,"endTime":920,"subject":"M","teachers":["MÜL"],"rooms":["204"],"text":"Kapitel 1-3"}]}}
        """.trimIndent())
        val e = UntisParser.exams(json)
        assertEquals(1, e.size)
        assertEquals("Klassenarbeit", e[0].type)
        assertEquals("204", e[0].rooms)
        assertEquals(LocalDate.of(2026, 10, 20), e[0].date)
    }

    @Test
    fun parsesSchoolLink() {
        assertEquals("csska.webuntis.com" to "csska",
            UntisAccount.parseLink("https://csska.webuntis.com/WebUntis/?school=csska#/basic/login"))
    }
}
