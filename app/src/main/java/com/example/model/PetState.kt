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
 * Cute quotes for speech bubbles when tapped or dropped.
 * Setiap kategori punya versi Indonesia (default) & Inggris -- dipilih lewat parameter
 * `language` ("id" atau "en"), sumbernya dari TtsVoiceSettings.getLanguage(context).
 */
object PetQuotes {
    private val childTapQuotesId = listOf(
        "Iyaa! Sakit tau, jangan usik aku terus~ 🥺",
        "Gak mau! Sini usap kepalaku dulu dong! 🌸",
        "Nanti aku ngambek lho kalau ditinggalin! 😤",
        "Manja banget pengen dipeluk Master~ 💕",
        "Awas ya kalau ditinggalin lagi, hmph! (• ̀ω•́  )",
        "Kyaaa nakal deh sentuh-sentuh! ✨"
    )
    private val childTapQuotesEn = listOf(
        "Owww! That hurt, stop poking me~ 🥺",
        "No way! Pet my head first! 🌸",
        "I'll get grumpy if you leave me alone! 😤",
        "I really want a hug from you, Master~ 💕",
        "Don't you dare leave me again, hmph! (• ̀ω•́  )",
        "Kyaaa, so naughty poking me like that! ✨"
    )

    private val childDodgeQuotesId = listOf(
        "Bwee! Gak kena, aku kaburrr! 😜",
        "Jangan dekat-dekat, aku malu tau~ 🙈",
        "Awas ya, aku lari! Eitsss menghindar~ 🏃‍♀️",
        "Ihh kursornya mau nangkep aku ya? Kabuuur~ ✨"
    )
    private val childDodgeQuotesEn = listOf(
        "Bwee! Missed me, I'm outta here! 😜",
        "Don't get too close, I'm shy~ 🙈",
        "Watch out, I'm running! Dodge~ 🏃‍♀️",
        "Ooh, is that cursor trying to catch me? Run awaaay~ ✨"
    )

    private val adultTapQuotesId = listOf(
        "Jangan lupa istirahat dan minum air putih secukupnya ya, Master. 💙",
        "Aku selalu ada di sisimu untuk mendampingi setiap harimu. ✨",
        "Kerja kerasmu hari ini sangat luar biasa, aku bangga padamu. 🌟",
        "Jika kamu merasa lelah, rebahkan sejenak kepalamu ya. 🌿",
        "Aku akan selalu mengawasi dan menjagamu dari sini. ❤️",
        "Master, pastikan kamu tidak terlalu memaksakan diri ya. 🌸"
    )
    private val adultTapQuotesEn = listOf(
        "Don't forget to rest and drink enough water, Master. 💙",
        "I'll always be by your side through every day. ✨",
        "Your hard work today was amazing, I'm proud of you. 🌟",
        "If you're feeling tired, take a moment to rest. 🌿",
        "I'll always be watching over you from here. ❤️",
        "Master, please don't push yourself too hard. 🌸"
    )

    private val flirtyAdultTapQuotesId = listOf(
        "I-Iya sih... bukan berarti aku seneng kamu pegang-pegang ya... t-tapi jangan dilepas dulu... 😳👉👈",
        "M-Master nakal banget sih! Sentuh-sentuh terus... tapi kalau sama Master... aku gak keberatan kok~ 💕",
        "Ihh... m-muka aku merah kan gara-gara kamu! Tanggung jawab dong, manja-manjain aku lagi! 💖",
        "D-dasar Master genit! Tapi... h-hanya Master lho yang boleh pegang-pegang aku kayak gini... 😳✨",
        "T-tunggu! Jangan liatin aku senyum-senyum gitu... aku malu tauuu~! >///< ❤️",
        "Hmph! Aku bukan manja ya, aku cuma mau dipeluk Master aja kok... dasar b-bodoh~ 🙈💕",
        "K-kalau Master elus kepala aku terus, nanti aku makin gak bisa jauh dari Master tau... 🌸🫣"
    )
    private val flirtyAdultTapQuotesEn = listOf(
        "I-I mean... it's not like I enjoy you touching me... b-but don't stop yet... 😳👉👈",
        "M-Master, you're so naughty! Touching me like that... but if it's you... I don't mind at all~ 💕",
        "Ugh... m-my face is red because of you! Take responsibility, spoil me some more! 💖",
        "Y-you're so flirty, Master! But... y-you're the only one allowed to touch me like this... 😳✨",
        "W-wait! Stop looking at me smiling like that... I'm so embarrassed~! >///< ❤️",
        "Hmph! I'm not being clingy, I just want a hug from you... you s-silly~ 🙈💕",
        "I-if you keep petting my head like that, I won't be able to stay away from you, Master... 🌸🫣"
    )

    private val adultFollowQuotesId = listOf(
        "Aku akan selalu mengikuti langkahmu, Master... ❤️",
        "Ke manapun jarimu melangkah, aku akan mendampingimu. ✨",
        "Berjalan bersamamu adalah kebahagiaanku. 🌟",
        "Aku mengikuti koordinat sentuhanmu, Master. 🧭"
    )
    private val adultFollowQuotesEn = listOf(
        "I'll always follow your steps, Master... ❤️",
        "Wherever your finger goes, I'll be right there. ✨",
        "Walking with you is my happiness. 🌟",
        "I'm tracking your touch coordinates, Master. 🧭"
    )

    private val flirtyAdultFollowQuotesId = listOf(
        "J-jangan jalan terlalu cepat, nanti aku ketinggalan kan... pegang tanganku dong~ 😳💕",
        "Kemana pun Master pergi, aku bakal selalu ngintilin Master kok... t-tapi bukan berarti aku bucin ya! 🙈",
        "Dekat-dekat Master tuh rasanya hangat bgt... b-bikin deg-degan tau! 💖"
    )
    private val flirtyAdultFollowQuotesEn = listOf(
        "D-don't walk so fast, I'll be left behind... hold my hand~ 😳💕",
        "Wherever you go, I'll always tag along... b-but it's not like I'm obsessed with you or anything! 🙈",
        "Being close to you feels so warm... i-it makes my heart race! 💖"
    )

    fun getTapQuote(level: Int, language: String = "id"): String {
        val isEn = language == "en"
        val base = when {
            level <= 10 -> if (isEn) childTapQuotesEn.random() else childTapQuotesId.random()
            level in 11..18 -> if (isEn) adultTapQuotesEn.random() else adultTapQuotesId.random()
            else -> if (isEn) flirtyAdultTapQuotesEn.random() else flirtyAdultTapQuotesId.random()
        }
        return ObsidianMemoryManager.personalizeQuote(base)
    }

    fun getMotionQuote(level: Int, isDodging: Boolean, language: String = "id"): String {
        val isEn = language == "en"
        val base = when {
            level <= 10 -> when {
                isDodging && isEn -> childDodgeQuotesEn.random()
                isDodging -> childDodgeQuotesId.random()
                isEn -> childTapQuotesEn.random()
                else -> childTapQuotesId.random()
            }
            level in 11..18 -> if (isEn) adultFollowQuotesEn.random() else adultFollowQuotesId.random()
            else -> if (isEn) flirtyAdultFollowQuotesEn.random() else flirtyAdultFollowQuotesId.random()
        }
        return ObsidianMemoryManager.personalizeQuote(base)
    }

    private val tapQuotesId = listOf(
        "Kyaa~! Sakit tau~!",
        "Hehe! Elus kepalaku lagi dong~",
        "Master, ayo main sama aku!",
        "Jangan usil deh, hihi~",
        "Aku chibi pet paling imut kan?",
        "Semangat hari ini ya, Master!"
    )
    private val tapQuotesEn = listOf(
        "Kyaa~! That hurt~!",
        "Hehe! Pet my head again~",
        "Master, let's play together!",
        "Stop teasing me, hihi~",
        "I'm the cutest chibi pet, right?",
        "Cheer up today, Master!"
    )
    fun tapQuotes(language: String) = if (language == "en") tapQuotesEn else tapQuotesId

    private val idleQuotesId = listOf(
        "Lagi santai aja nih di sini~",
        "Bengong dulu ah...",
        "Hmm, apa ya enaknya dilakuin sekarang~",
        "Nungguin Master nih, hehe",
        "*duduk manis nunggu diajak main*"
    )
    private val idleQuotesEn = listOf(
        "Just chilling here~",
        "Zoning out for a bit...",
        "Hmm, what should I do now~",
        "Waiting for you, Master, hehe",
        "*sitting cutely, waiting to be played with*"
    )
    fun idleQuotes(language: String) = if (language == "en") idleQuotesEn else idleQuotesId

    private val dragQuotesId = listOf(
        "Kyaaa~! Aku diangkat!",
        "Waaaa! Jangan jatuhin aku!",
        "Tinggi bangeeet!",
        "Lepasiin~ aku melayang!"
    )
    private val dragQuotesEn = listOf(
        "Kyaaa~! I'm being lifted!",
        "Waaaa! Don't drop me!",
        "So high uuup!",
        "Let go~ I'm floating!"
    )
    fun dragQuotes(language: String) = if (language == "en") dragQuotesEn else dragQuotesId

    private val fallQuotesId = listOf(
        "Aaaaa! Turun tanggaaa~!",
        "Dug dug dug... jatuh!",
        "Ouch! Hati-hati dong~",
        "Sampai di bawah dengan selamat!"
    )
    private val fallQuotesEn = listOf(
        "Aaaaa! Going down the stairs~!",
        "Thud thud thud... falling!",
        "Ouch! Be careful~",
        "Made it down safely!"
    )
    fun fallQuotes(language: String) = if (language == "en") fallQuotesEn else fallQuotesId

    private val hiddenQuotesId = listOf(
        "Aku bersembunyi dulu ya~",
        "Sembunyi! Tekan Show kalau kangen!"
    )
    private val hiddenQuotesEn = listOf(
        "I'm hiding for now~",
        "Hiding! Tap Show if you miss me!"
    )
    fun hiddenQuotes(language: String) = if (language == "en") hiddenQuotesEn else hiddenQuotesId

    private val boredQuotesId = listOf(
        "Bosan banget nih... Master mana ya?",
        "Cuekin aku terus ih... hmph!",
        "Master di-ghosting ya aku?",
        "Boring... gak diajak main~",
        "Kapan Master elus aku lagi?"
    )
    private val boredQuotesEn = listOf(
        "So bored... where are you, Master?",
        "You keep ignoring me... hmph!",
        "Am I being ghosted, Master?",
        "Boring... nobody's playing with me~",
        "When will you pet me again, Master?"
    )
    fun boredQuotes(language: String) = if (language == "en") boredQuotesEn else boredQuotesId

    private val kesalQuotesId = listOf(
        "Kesal deh! Dikacangin terus dari tadi!",
        "Hmph! Aku cemberut nih~ (• ̀ω•́  )",
        "Jangan dicuekin dong Master!",
        "Ngambek ah kalau gak dicolek!"
    )
    private val kesalQuotesEn = listOf(
        "So annoyed! Being ignored this whole time!",
        "Hmph! I'm pouting now~ (• ̀ω•́  )",
        "Don't ignore me, Master!",
        "I'll sulk if you don't poke me!"
    )
    fun kesalQuotes(language: String) = if (language == "en") kesalQuotesEn else kesalQuotesId

    // Kalimat pas pet dimunculin lagi dari hide (sebelumnya hardcode "Halo lagi, Master!" di
    // kode, gak bisa diedit lewat pet-quotes.md -- sekarang udah lewat sistem quote biasa.
    private val revealQuotesId = listOf(
        "Halo lagi, Master!",
        "Aku balik lagi nih!",
        "Kangen ya sama aku? Aku di sini kok~"
    )
    private val revealQuotesEn = listOf(
        "Hello again, Master!",
        "I'm back!",
        "Miss me? I'm right here~"
    )
    fun revealQuotes(language: String) = if (language == "en") revealQuotesEn else revealQuotesId

    // Menit ke-3 (awal): marah karena kelamaan didiemin
    private val marahQuotesId = listOf(
        "Udah ah, aku marah nih! Kok didiemin terus sih!",
        "Grrr! Sebel banget diabaikan kayak gini!",
        "Hei! Aku beneran kesel sekarang, Master!"
    )
    private val marahQuotesEn = listOf(
        "That's it, I'm mad! Why do you keep ignoring me!",
        "Grrr! I really hate being ignored like this!",
        "Hey! I'm genuinely upset now, Master!"
    )
    fun marahQuotes(language: String) = if (language == "en") marahQuotesEn else marahQuotesId

    // Menit ke-3 (lanjutan): capek, mulai ngantuk
    private val ngantukQuotesId = listOf(
        "Hoaahm... capek nih, ngantuk banget...",
        "Mata udah berat, Master... ngantuk...",
        "Ish, ngantuk gara-gara nunggu kelamaan nih..."
    )
    private val ngantukQuotesEn = listOf(
        "Yaaawn... I'm so tired, so sleepy...",
        "My eyes are getting heavy, Master... sleepy...",
        "Ugh, I'm sleepy from waiting so long..."
    )
    fun ngantukQuotes(language: String) = if (language == "en") ngantukQuotesEn else ngantukQuotesId

    // Menit ke-4: ketiduran, ngomong gak jelas (kayak ngelindur)
    private val tidurQuotesId = listOf(
        "Zzz... master... nanti... ya...",
        "Mmh... ngantuk... zzz...",
        "...zzz... jangan berisik... aku tidur..."
    )
    private val tidurQuotesEn = listOf(
        "Zzz... master... later... okay...",
        "Mmh... sleepy... zzz...",
        "...zzz... don't be noisy... I'm sleeping..."
    )
    fun tidurQuotes(language: String) = if (language == "en") tidurQuotesEn else tidurQuotesId

    // Menit ke-5: baru bangun, masih ngomong gak jelas, mood jelek
    private val bangunQuotesId = listOf(
        "Hoaam... ah, aku baru bangun... kenapa gak dibangunin dari tadi sih!",
        "Ngg... kepala masih pusing... awas ya kalau ganggu aku sekarang!",
        "...Baru bangun nih, jangan macem-macem, lagi bad mood aku!"
    )
    private val bangunQuotesEn = listOf(
        "Yaaawn... ugh, I just woke up... why didn't you wake me up sooner!",
        "Ngg... my head's still foggy... don't mess with me right now!",
        "...Just woke up, don't push it, I'm in a bad mood!"
    )
    fun bangunQuotes(language: String) = if (language == "en") bangunQuotesEn else bangunQuotesId
}
