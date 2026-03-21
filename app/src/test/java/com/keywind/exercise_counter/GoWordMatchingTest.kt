package com.keywind.exercise_counter

import com.keywind.exercise_counter.audio.SpeechRecognitionHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test #2: Go-word matching logic in SpeechRecognitionHelper.
 *
 * To demonstrate failure: remove "ready" from GO_WORDS in SpeechRecognitionHelper.
 * testMatchesReady and testMatchesReadyInSentence will fail.
 *
 * Or change `contains(word, ignoreCase = true)` to `equals(word)` —
 * testMatchesReadyInSentence and testMatchesGoInSentence will fail.
 */
class GoWordMatchingTest {

    // --- positive matches ---

    @Test
    fun testMatchesReady() {
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("ready")))
    }

    @Test
    fun testMatchesReadyCaseInsensitive() {
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("READY")))
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("Ready")))
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("rEaDy")))
    }

    @Test
    fun testMatchesReadyInSentence() {
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("I'm ready now")))
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("yeah I'm ready")))
    }

    @Test
    fun testMatchesGo() {
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("go")))
    }

    @Test
    fun testMatchesGoInSentence() {
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("let's go")))
    }

    @Test
    fun testMatchesNext() {
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("next")))
    }

    @Test
    fun testMatchesOkay() {
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("okay")))
    }

    @Test
    fun testMatchesOk() {
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("ok")))
    }

    @Test
    fun testMatchesWhen() {
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("when")))
    }

    @Test
    fun testMatchesInSecondResult() {
        // Only the second alternative contains a go-word
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("hello", "ready")))
    }

    // --- negative matches ---

    @Test
    fun testNoMatchOnEmptyList() {
        assertFalse(SpeechRecognitionHelper.containsGoWord(emptyList()))
    }

    @Test
    fun testNoMatchOnIrrelevantWord() {
        assertFalse(SpeechRecognitionHelper.containsGoWord(listOf("hello")))
        assertFalse(SpeechRecognitionHelper.containsGoWord(listOf("start")))
        assertFalse(SpeechRecognitionHelper.containsGoWord(listOf("yes")))
    }

    @Test
    fun testNoMatchOnEmptyString() {
        assertFalse(SpeechRecognitionHelper.containsGoWord(listOf("")))
    }

    // --- false-positive awareness ---

    @Test
    fun testReadingContainsNoGoWord() {
        // "reading" contains no go-word — "go" is not a substring of "reading"
        // This documents expected behavior rather than asserting a bug.
        assertFalse(SpeechRecognitionHelper.containsGoWord(listOf("reading")))
    }

    @Test
    fun testOkayContainedInLongWord() {
        // "okaydoke" contains "okay" — substring match is intentional
        assertTrue(SpeechRecognitionHelper.containsGoWord(listOf("okaydoke")))
    }
}
