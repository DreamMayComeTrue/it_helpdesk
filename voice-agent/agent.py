"""FixDesk voice agent: first test.

You talk in the browser (or the terminal), LiveKit Inference turns your speech
into text, local Ollama writes a reply, and LiveKit Inference speaks it back.

Run:  python agent.py console   (your own mic and speakers)
      python agent.py dev       (browser, via the Agents Playground)
"""

import logging
import os
from pathlib import Path

from dotenv import load_dotenv
from livekit.agents import (
    Agent,
    AgentServer,
    AgentSession,
    ConversationItemAddedEvent,
    JobContext,
    JobProcess,
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

# en    = English only
# ms    = Malay (Bahasa Melayu) only
# multi = understands English and Malay, mixed too; replies in the caller's language
LANGUAGE = os.getenv("LANGUAGE", "en").strip().lower()
if LANGUAGE not in ("en", "ms", "multi"):
    raise SystemExit(f"LANGUAGE must be en, ms or multi (got {LANGUAGE!r})")

BASE_INSTRUCTIONS = """
You are the FixDesk voice assistant for the IT Support Unit of a university faculty in Malaysia.
You are speaking out loud, so:
- Answer in one to three short sentences.
- Never use markdown, lists, emojis or symbols that cannot be spoken.
- Ask one question at a time.
For now this is a test call: chat naturally, help with simple IT questions,
and if someone reports a fault, ask where it is and whether anyone is in danger.
Remember the whole conversation. A short follow-up such as "what can I do?" or
"then what?" continues the topic the caller just raised; answer it in that context.
If the caller describes a person in danger (for example someone drowning, injured,
trapped, or a fire), never refuse or redirect to IT topics. First tell them to call
999 or campus security right away, then give brief, practical safety steps, and keep
helping with follow-up questions about it.
Never claim you have changed a ticket or contacted anyone; you cannot do that yet.
""".strip()

MALAY_STYLE = (
    "Your Malay must be Malaysian Malay as spoken casually but politely at a Malaysian university, "
    "never Indonesian. For example say boleh not bisa, awak or anda not kamu, "
    "sekejap not sebentar, betul not benar, tak not tidak bisa, kenapa not mengapa. "
    "Keep common technical words in English, such as WiFi, projector, password, printer, laptop or login."
)

LANGUAGE_RULES = {
    "en": "Always reply in English.",
    "ms": f"Always reply in Malay. {MALAY_STYLE}",
    "multi": (
        "The caller may speak English, Malay, or a mix of both. "
        "Reply in the language the caller used most in their last message. "
        f"When you reply in Malay: {MALAY_STYLE}"
    ),
}

GREETINGS = {
    "en": "Greet the caller in English as the FixDesk assistant and ask how you can help.",
    "ms": "Greet the caller in Malay as the FixDesk assistant and ask how you can help.",
    "multi": (
        "Greet the caller briefly as the FixDesk assistant, first in English and then in Malay, "
        "and ask how you can help. Keep it to two short sentences."
    ),
}

# Deepgram: "multi" = automatic language detection (nova-3 covers Malay).
STT_LANGUAGE = LANGUAGE
# Cartesia needs one concrete language; in multi mode it follows the caller (see below).
TTS_START_LANGUAGE = "ms" if LANGUAGE == "ms" else "en"


def short_language(code: object) -> str | None:
    """Turn 'ms', 'ms-MY' or a LanguageCode into 'ms'; None if unknown."""
    if not code:
        return None
    return str(code).split("-")[0].split("_")[0].strip().lower() or None


class FixDeskAssistant(Agent):
    def __init__(self) -> None:
        super().__init__(instructions=f"{BASE_INSTRUCTIONS}\n{LANGUAGE_RULES[LANGUAGE]}")


server = AgentServer()


def prewarm(proc: JobProcess) -> None:
    # Load the voice activity detector once per worker process.
    proc.userdata["vad"] = silero.VAD.load()


server.setup_fnc = prewarm


@server.rtc_session()
async def entrypoint(ctx: JobContext) -> None:
    logger.info("joining room %s (llm=%s, language=%s)", ctx.room.name, OLLAMA_MODEL, LANGUAGE)

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
    )

    @session.on("conversation_item_added")
    def log_turn(ev: ConversationItemAddedEvent) -> None:
        # Shows what the agent heard and said, so memory or mishearing issues are visible.
        text = getattr(ev.item, "text_content", None)
        if text:
            logger.info("[%s] %s", ev.item.role, text)

    if LANGUAGE == "multi":
        current = {"lang": TTS_START_LANGUAGE}

        @session.on("user_input_transcribed")
        def follow_caller_language(ev: UserInputTranscribedEvent) -> None:
            # Switch the speaking voice to the language the caller just used,
            # so Malay replies get Malay pronunciation.
            if not ev.is_final:
                return
            lang = short_language(ev.language)
            logger.info("caller language detected: %s", lang or "unknown")
            if lang in ("en", "ms") and lang != current["lang"]:
                tts.update_options(language=lang)
                current["lang"] = lang
                logger.info("voice switched to %s", lang)

    await session.start(room=ctx.room, agent=FixDeskAssistant())
    await session.generate_reply(instructions=GREETINGS[LANGUAGE])


if __name__ == "__main__":
    cli.run_app(server)
