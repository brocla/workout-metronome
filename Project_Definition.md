# Exercise Counter #

Provide a metronme like signal to do the counting for a person doing exercises.  

Example: Exercise instruction is to do 3 sets of exercie for 10 seconds each.

The metronome begins with a BEAT every 2 seconds. After 10 seconds DURation it counts out loud "ONE SET". The pauses for a GAP time of 1 second. Then starts the BEAT again. At the end of another 10 seconds it counts out load TWO SETS. Then the GAP of 1 sec again, then the BEAT again, then after antoher 10 set SET, count out load DONE.  The app stops.

There are 4 vertical scroll wheels pickers on the mainscreen. One each for SETS, DUR, GAP, BEAT.

Under those scroll wheels is a row of two buttons. A play/pause button, a reset button.

## Stack ##
This is an android project using Kotlin, JetPack, and Compose.

## Vertical Scroll Wheel Picker ##
A snapping vertical number wheel for each of the four values — the user flicks up/down and it snaps to the nearest integer. This is essentially the iOS-style picker wheel adapted for Compose.

In Compose, there's no built-in wheel picker, but it's quite achievable with a LazyColumn + SnapFlingBehavior (from the Compose Foundation snapping APIs). The core idea:

A LazyColumn showing ~3-5 visible rows, with the center row highlighted
rememberLazyListState() + snapping so it always lands on a number
A semi-transparent gradient/fade on the top and bottom items to draw the eye to the center.

## Audio ##
AudioTrack is the best choice for steady-beat timing.

Generates audio at the sample level, so you control timing precisely
You can synthesize a simple click/tick sound programmatically (short sine burst or white noise pop) — no sound file needed
Runs on its own thread, decoupled from UI rendering, so frame drops don't affect beat timing

For Counting Numbers Aloud use Android TextToSpeech (TTS). Coordinate both audio streams. Mix the tick and the TTS output on the same timeline, or at minimum trigger TTS slightly ahead of the beat to account for its startup latency.

## Intended Audience ## 
This project is for myself, family and friends. I never plan on publishing to Google Play so there is no need for a privacy statement or other items required by that platform. There is also no interest in monetizing the app. Or in gathering user info.

I will likely publish the code to github as open source. Be careful to properly gaurd secrets like API keys from the beginning so it won't be an issue later.

## Code Quality ##
The code will be on github so my reputation will be affected by the quality of the code. Go the the extra effort to write code that could pass a review with Zac Sweers.