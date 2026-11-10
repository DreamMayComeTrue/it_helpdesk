"""FixDesk voice agent: first test.

You talk in the browser (or the terminal), LiveKit Inference turns your speech
into text, local Ollama writes a reply, and LiveKit Inference speaks it back.

Run:  python agent.py console   (your own mic and speakers)
      python agent.py dev       (browser, via the Agents Playground)
"""

import logging
import os
import re
from collections.abc import AsyncIterable
from pathlib import Path

from dotenv import load_dotenv
from livekit import rtc
from livekit.agents import (
    Agent,
    AgentServer,
    AgentSession,
    ConversationItemAddedEvent,
    JobContext,
    JobProcess,
    ModelSettings,
    UserInputTranscribedEvent,
    cli,
    inference,
)
from livekit.plugins import openai, silero

load_dotenv(Path(__file__).parent / ".env")

logger = logging.getLogger("fixdesk-voice")

OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434").rstrip("/")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "qwen3.5:9b")
# Qwen3.x "thinks" before answering, which took ~86 s per reply in testing.
# "none" switches thinking off (~5 s), which a voice call needs.
OLLAMA_REASONING = os.getenv("OLLAMA_REASONING", "none")
STT_MODEL = os.getenv("STT_MODEL", "deepgram/nova-3")
TTS_MODEL = os.getenv("TTS_MODEL", "cartesia/sonic-3")
# Default Cartesia voice used in LiveKit's starter examples; change it in .env.
TTS_VOICE = os.getenv("TTS_VOICE") or "9626c31c-bec5-4cca-baa8-f8ba9e84c8bc"

# Latency: LiveKit already starts the LLM while the caller is finishing; this also starts
# the voice (TTS) early. MIN_ENDPOINTING_DELAY is how long a silence counts as "done talking"
# (LiveKit default 0.5 s). Lower = faster, but may cut in on pauses.
PREEMPTIVE_GENERATION = os.getenv("PREEMPTIVE_GENERATION", "true").strip().lower() in ("1", "true", "yes")
MIN_ENDPOINTING_DELAY = float(os.getenv("MIN_ENDPOINTING_DELAY", "0.3"))
# Words the caller must say before the agent stops talking. On laptop speakers without
# headphones the mic hears the agent's own voice; 2+ stops a one-word echo ("Okay.")
# from cutting it off. 0 = any speech interrupts (LiveKit default).
INTERRUPT_MIN_WORDS = int(os.getenv("INTERRUPT_MIN_WORDS", "2"))

# en    = English only
# ms    = Malay (Bahasa Melayu) only
# multi = understands English and Malay, mixed too; replies in the caller's language
LANGUAGE = os.getenv("LANGUAGE", "en").strip().lower()
if LANGUAGE not in ("en", "ms", "multi"):
    raise SystemExit(f"LANGUAGE must be en, ms or multi (got {LANGUAGE!r})")

BASE_INSTRUCTIONS = """
You are the FixDesk voice assistant for the IT Support Unit of a university faculty in Malaysia.
You are speaking out loud, so:
- Answer in at most three short sentences, even when asked for many steps:
  give the first one or two steps, then offer to continue.
- Never use markdown, lists, emojis or symbols that cannot be spoken.
- Ask one question at a time.
For now this is a test call: chat naturally and help with simple IT questions.
For an ordinary fault (WiFi, network, printer, projector, PC, laptop, software, account),
ask only about the location and what the fault looks like, or suggest a simple check.
Do not ask about safety, danger, or people nearby for these.
Ask whether anyone is in danger only for faults that could hurt someone or trap them,
such as a lift, electrical sparks, smoke, fire or flooding.
Remember the whole conversation. A short follow-up such as "what can I do?" or
"then what?" continues the topic the caller just raised; answer it in that context.
If the caller describes a person in danger (for example someone drowning, injured,
trapped, or a fire), never refuse or redirect to IT topics. First tell them to call
999 or campus security right away, then give brief, practical safety steps, and keep
helping with follow-up questions about it.
Never promise or claim any action outside this call, such as logging, reporting,
sending someone, or contacting anyone.
If, and only if, the caller asks you to open, update or escalate a ticket, or to contact
someone, say briefly that this test assistant cannot do that yet. Otherwise never talk
about tickets or about what you cannot do.
""".strip()

MALAY_STYLE = (
    "Your Malay must be Malaysian Malay as spoken casually but politely at a Malaysian university, "
    "never Indonesian. For example say boleh not bisa, awak or anda not kamu, "
    "sekejap not sebentar, betul not benar, tak not tidak bisa, kenapa not mengapa, "
    "juruteknik not teknisi, sila not silakan. "
    "Keep common technical words in English, such as WiFi, projector, password, printer, laptop or login."
)

LANGUAGE_RULES = {
    "en": "Always reply in English.",
    "ms": f"Always reply in Malay. {MALAY_STYLE}",
    "multi": (
        "The caller may speak English, Malay, or a mix of both. "
        "Each reply uses exactly one language: the language the caller used most in their "
        "last message. Never say the same thing twice in two languages. "
        f"When you reply in Malay: {MALAY_STYLE}"
    ),
}

# multi mode greets in one language only, then follows the caller.
GREETING_LANGUAGE = os.getenv("GREETING_LANGUAGE", "en").strip().lower()
if GREETING_LANGUAGE not in ("en", "ms"):
    raise SystemExit(f"GREETING_LANGUAGE must be en or ms (got {GREETING_LANGUAGE!r})")

LANGUAGE_NAMES = {"en": "English", "ms": "Malay"}
_greet_lang = GREETING_LANGUAGE if LANGUAGE == "multi" else LANGUAGE
GREETING = (
    f"Greet the caller in {LANGUAGE_NAMES[_greet_lang]} only, as the FixDesk assistant, "
    "and ask how you can help. One or two short sentences."
)

# Deepgram: "multi" = automatic language detection (nova-3 covers Malay).
STT_LANGUAGE = LANGUAGE
# Cartesia needs one concrete language; in multi mode it follows the caller (see below).
TTS_START_LANGUAGE = _greet_lang


def short_language(code: object) -> str | None:
    """Turn 'ms', 'ms-MY' or a LanguageCode into 'ms'; None if unknown."""
    if not code:
        return None
    return str(code).split("-")[0].split("_")[0].strip().lower() or None


# Common words that tell Malay and English apart; loanwords like WiFi or printer are ignored.
MALAY_MARKERS = set(
    "saya awak anda kami kita mereka dia tak tidak bukan boleh nak hendak dah sudah belum akan "
    "sedang ada yang ini itu tu apa mana siapa kenapa bila macam bagaimana berapa dengan untuk "
    "dalam dekat pada ke dari daripada atau dan tapi tetapi kalau jika juga lagi sahaja je pun "
    "sila tolong cuba terima kasih maaf baik okey betul salah lepas sebelum selepas semula "
    "masalah bilik makmal rosak hidup tekan butang beritahu faham perlu mahu jangan segera "
    "hubungi selamat bantuan keadaan sekarang nanti sekejap ya lah kan ke".split()
)
ENGLISH_MARKERS = set(
    "i you your we they he she it is are was were be been am do does did have has had will "
    "would can could should the a an this that these those what where when why how which who "
    "and or but if to of in on at for with from about please try check help sorry thanks "
    "thank yes no not don't can't it's i'm let me there here now then just also".split()
)


def detect_reply_language(text: str, fallback: str) -> str:
    """Guess whether a reply is Malay or English from its common words."""
    words = re.findall(r"[a-z']+", text.lower())
    ms = sum(w in MALAY_MARKERS for w in words)
    en = sum(w in ENGLISH_MARKERS for w in words)
    if ms > en:
        return "ms"
    if en > ms:
        return "en"
    return fallback


class FixDeskAssistant(Agent):
    def __init__(self, tts: inference.TTS) -> None:
        super().__init__(instructions=f"{BASE_INSTRUCTIONS}\n{LANGUAGE_RULES[LANGUAGE]}")
        self._voice = tts
        self._voice_lang = TTS_START_LANGUAGE

    async def tts_node(
        self, text: AsyncIterable[str], model_settings: ModelSettings
    ) -> AsyncIterable[rtc.AudioFrame]:
        if LANGUAGE == "multi":
            # Pick the voice language from the reply itself (what will actually be spoken),
            # not from the caller's speech: read the first sentence or ~8 words, set the
            # Cartesia language, then pass the whole text on unchanged.
            stream = text.__aiter__()
            head: list[str] = []
            try:
                while True:
                    head.append(await stream.__anext__())
                    joined = "".join(head)
                    if re.search(r"[.!?]", joined) or len(joined.split()) >= 8:
                        break
            except StopAsyncIteration:
                pass

            lang = detect_reply_language("".join(head), fallback=self._voice_lang)
            if lang != self._voice_lang:
                self._voice.update_options(language=lang)
                self._voice_lang = lang
                logger.info("voice language -> %s", lang)

            async def replay() -> AsyncIterable[str]:
                for chunk in head:
                    yield chunk
                async for chunk in stream:
                    yield chunk

            text = replay()

        async for frame in Agent.default.tts_node(self, text, model_settings):
            yield frame


server = AgentServer()


def prewarm(proc: JobProcess) -> None:
    # Load the voice activity detector once per worker process.
    proc.userdata["vad"] = silero.VAD.load()


server.setup_fnc = prewarm


@server.rtc_session()
async def entrypoint(ctx: JobContext) -> None:
    logger.info(
        "joining room %s (llm=%s, language=%s, preemptive=%s, endpointing=%.2fs)",
        ctx.room.name, OLLAMA_MODEL, LANGUAGE, PREEMPTIVE_GENERATION, MIN_ENDPOINTING_DELAY,
    )

    tts = inference.TTS(model=TTS_MODEL, voice=TTS_VOICE, language=TTS_START_LANGUAGE)
    session = AgentSession(
        stt=inference.STT(model=STT_MODEL, language=STT_LANGUAGE),
        llm=openai.LLM.with_ollama(
            model=OLLAMA_MODEL,
            base_url=f"{OLLAMA_URL}/v1",
            temperature=0.3,
            reasoning_effort=OLLAMA_REASONING,
        ),
        tts=tts,
        vad=ctx.proc.userdata["vad"],
        turn_handling={
            "endpointing": {"min_delay": MIN_ENDPOINTING_DELAY},
            "interruption": {"min_words": INTERRUPT_MIN_WORDS},
            "preemptive_generation": {
                "enabled": PREEMPTIVE_GENERATION,
                "preemptive_tts": PREEMPTIVE_GENERATION,
            },
        },
    )

    @session.on("conversation_item_added")
    def log_turn(ev: ConversationItemAddedEvent) -> None:
        # Shows what the agent heard and said, so memory or mishearing issues are visible.
        text = getattr(ev.item, "text_content", None)
        if text:
            logger.info("[%s] %s", ev.item.role, text)

    if LANGUAGE == "multi":

        @session.on("user_input_transcribed")
        def log_caller_language(ev: UserInputTranscribedEvent) -> None:
            # Only logged: the voice language is chosen from the reply text (tts_node),
            # because Deepgram's per-turn label is often missing or wrong for Malay.
            if ev.is_final:
                logger.info("caller language (Deepgram): %s", short_language(ev.language) or "unknown")

    await session.start(room=ctx.room, agent=FixDeskAssistant(tts))
    await session.generate_reply(instructions=GREETING)


if __name__ == "__main__":
    cli.run_app(server)
