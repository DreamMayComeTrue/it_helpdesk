# FixDesk voice agent (bonus)

A Python LiveKit agent you can talk to. It is a separate service; later it will talk to FixDesk only through `/api/agent/**`.

How a turn works:

1. **Ears:** LiveKit Inference speech-to-text (`deepgram/nova-3`) turns your voice into text.
2. **Brain:** local Ollama (`qwen3.5:9b` by default, thinking switched off for fast replies) writes the reply.
3. **Mouth:** LiveKit Inference text-to-speech (`cartesia/sonic-3`) speaks it back.

LiveKit Cloud carries the audio between your browser and the agent.

## Setup (Windows, PowerShell)

```powershell
cd voice-agent
py -3 -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env   # then fill in LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET
```

Make sure Ollama is running and the model is pulled:

```powershell
ollama pull qwen3.5:9b
```

## Run

```powershell
.venv\Scripts\python.exe agent.py download-files   # first time only (voice activity model)
.venv\Scripts\python.exe agent.py dev
```

Quickest test, talking through your own microphone and speakers in the terminal (headphones recommended):

```powershell
.venv\Scripts\python.exe agent.py console
```

Or open the **Agents Playground** at <https://agents-playground.livekit.io>, connect to your LiveKit Cloud project, allow the microphone and start talking.

## Language (English / Malay)

Set `LANGUAGE` in `.env`:

| Value | Listens for | Replies in |
| --- | --- | --- |
| `en` | English | English |
| `ms` | Malay (Bahasa Melayu) | Malay |
| `multi` | English, Malay, or a mix | the language the caller used; the voice switches pronunciation to match |

Malay needs a model that supports it: `deepgram/nova-3` (listening) and `cartesia/sonic-3` or newer (speaking). `cartesia/sonic-2` is retired and has no Malay.

## Latency

Console mode prints each turn's timing (`end_of_turn`, `llm_ttft`, `tts_ttfb`, `e2e`). Two `.env` settings shorten it:

- `PREEMPTIVE_GENERATION=true`: the reply is written and voiced while you are still finishing your sentence (LiveKit does the writing part by default; this adds the voice).
- `MIN_ENDPOINTING_DELAY=0.3`: seconds of silence before your turn counts as finished. Raise it to `0.5` if the agent cuts in when you pause.

Echo: console mode has no echo cancellation, so on laptop speakers the mic can hear the agent and log its words as `[user]` (for example `[user] Okay.`), cutting it off. Use headphones, or use `dev` with the Agents Playground (the browser cancels echo). `INTERRUPT_MIN_WORDS=2` stops one-word echoes from interrupting.

A smaller local model does not help much on an integrated GPU: qwen3.5:9b, qwen2.5:7b and qwen3:8b all measured about the same time to first token.

## Notes

- `.env` holds your LiveKit secret; it is git-ignored. Never commit it.
- The agent cannot change tickets yet. Proposing actions through the Agent API comes later.
