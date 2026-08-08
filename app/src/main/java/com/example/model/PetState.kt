package com.example.model

import com.example.data.ObsidianMemoryManager

/**
 * Expression / Pose state for the Chibi Pet
 */
enum class PetPose {
    IDLE,       // Standing / sitting cutely
    HELD,       // Lifted / dragged up by finger
    FALLING,    // Step-falling down
    HAPPY,      // Tapped / petted
    SLEEPING    // Resting at bottom
}

/**
 * Gravity Fall Mode
 */
enum class FallPhysicsMode {
    STAIR_STEP, // Descends step-by-step with horizontal stair wobble
    SMOOTH      // Direct smooth gravity fall
}

/**
 * Cute quotes for speech bubbles when tapped or dropped
 */
object PetQuotes {
    val childTapQuotes = listOf(
        "Iyaa! Sakit tau, jangan usik aku terus~ 🥺",
        "Gak mau! Sini usap kepalaku dulu dong! 🌸",
        "Nanti aku ngambek lho kalau ditinggalin! 😤",
        "Manja banget pengen dipeluk Master~ 💕",
        "Awas ya kalau ditinggalin lagi, hmph! (• ̀ω•́  )",
        "Kyaaa nakal deh sentuh-sentuh! ✨"
    )

    val childDodgeQuotes = listOf(
        "Bwee! Gak kena, aku kaburrr! 😜",
        "Jangan dekat-dekat, aku malu tau~ 🙈",
        "Awas ya, aku lari! Eitsss menghindar~ 🏃‍♀️",
        "Ihh kursornya mau nangkep aku ya? Kabuuur~ ✨"
    )

    val adultTapQuotes = listOf(
        "Jangan lupa istirahat dan minum air putih secukupnya ya, Master. 💙",
        "Aku selalu ada di sisimu untuk mendampingi setiap harimu. ✨",
        "Kerja kerasmu hari ini sangat luar biasa, aku bangga padamu. 🌟",
        "Jika kamu merasa lelah, rebahkan sejenak kepalamu ya. 🌿",
        "Aku akan selalu mengawasi dan menjagamu dari sini. ❤️",
        "Master, pastikan kamu tidak terlalu memaksakan diri ya. 🌸"
    )

    val flirtyAdultTapQuotes = listOf(
        "I-Iya sih... bukan berarti aku seneng kamu pegang-pegang ya... t-tapi jangan dilepas dulu... 😳👉👈",
        "M-Master nakal banget sih! Sentuh-sentuh terus... tapi kalau sama Master... aku gak keberatan kok~ 💕",
        "Ihh... m-muka aku merah kan gara-gara kamu! Tanggung jawab dong, manja-manjain aku lagi! 💖",
        "D-dasar Master genit! Tapi... h-hanya Master lho yang boleh pegang-pegang aku kayak gini... 😳✨",
        "T-tunggu! Jangan liatin aku senyum-senyum gitu... aku malu tauuu~! >///< ❤️",
        "Hmph! Aku bukan manja ya, aku cuma mau dipeluk Master aja kok... dasar b-bodoh~ 🙈💕",
        "K-kalau Master elus kepala aku terus, nanti aku makin gak bisa jauh dari Master tau... 🌸🫣"
    )

    val adultFollowQuotes = listOf(
        "Aku akan selalu mengikuti langkahmu, Master... ❤️",
        "Ke manapun jarimu melangkah, aku akan mendampingimu. ✨",
        "Berjalan bersamamu adalah kebahagiaanku. 🌟",
        "Aku mengikuti koordinat sentuhanmu, Master. 🧭"
    )

    val flirtyAdultFollowQuotes = listOf(
        "J-jangan jalan terlalu cepat, nanti aku ketinggalan kan... pegang tanganku dong~ 😳💕",
        "Kemana pun Master pergi, aku bakal selalu ngintilin Master kok... t-tapi bukan berarti aku bucin ya! 🙈",
        "Dekat-dekat Master tuh rasanya hangat bgt... b-bikin deg-degan tau! 💖"
    )

    fun getTapQuote(level: Int): String {
        val base = when {
            level <= 10 -> childTapQuotes.random()
            level in 11..18 -> adultTapQuotes.random()
            else -> flirtyAdultTapQuotes.random()
        }
        return ObsidianMemoryManager.personalizeQuote(base)
    }

    fun getMotionQuote(level: Int, isDodging: Boolean): String {
        val base = when {
            level <= 10 -> if (isDodging) childDodgeQuotes.random() else childTapQuotes.random()
            level in 11..18 -> adultFollowQuotes.random()
            else -> flirtyAdultFollowQuotes.random()
        }
        return ObsidianMemoryManager.personalizeQuote(base)
    }

    val tapQuotes = listOf(
        "Kyaa~! Sakit tau~!",
        "Hehe! Elus kepalaku lagi dong~",
        "Master, ayo main sama aku!",
        "Jangan usil deh, hihi~",
        "Aku chibi pet paling imut kan?",
        "Semangat hari ini ya, Master!"
    )

    val idleQuotes = listOf(
        "Lagi santai aja nih di sini~",
        "Bengong dulu ah...",
        "Hmm, apa ya enaknya dilakuin sekarang~",
        "Nungguin Master nih, hehe",
        "*duduk manis nunggu diajak main*"
    )

    val dragQuotes = listOf(
        "Kyaaa~! Aku diangkat!",
        "Waaaa! Jangan jatuhin aku!",
        "Tinggi bangeeet!",
        "Lepasiin~ aku melayang!"
    )

    val fallQuotes = listOf(
        "Aaaaa! Turun tanggaaa~!",
        "Dug dug dug... jatuh!",
        "Ouch! Hati-hati dong~",
        "Sampai di bawah dengan selamat!"
    )

    val hiddenQuotes = listOf(
        "Aku bersembunyi dulu ya~",
        "Sembunyi! Tekan Show kalau kangen!"
    )

    val boredQuotes = listOf(
        "Bosan banget nih... Master mana ya?",
        "Cuekin aku terus ih... hmph!",
        "Master di-ghosting ya aku?",
        "Boring... gak diajak main~",
        "Kapan Master elus aku lagi?"
    )

    val kesalQuotes = listOf(
        "Kesal deh! Dikacangin terus dari tadi!",
        "Hmph! Aku cemberut nih~ (• ̀ω•́  )",
        "Jangan dicuekin dong Master!",
        "Ngambek ah kalau gak dicolek!"
    )
}
