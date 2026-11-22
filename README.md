# Tenki no Kotae （天気のこたえ）

A weather app that shows no numbers — only the answer.

Instead of temperatures and percentages, it tells you what to do:
what to wear compared with yesterday, whether you need an umbrella,
and whether sunscreen is worth it. One glance in the morning is enough
to decide and leave the house.

## Features

- A one-line conclusion plus three answers: clothing / umbrella / sunscreen
- Advice based on apparent temperature compared with yesterday (same place, same scale)
- Animated sky that reflects the time of day and the weather
- "Today's person": a character whose outfit changes with the forecast,
  composed from layered parts (outfits, umbrella, cap, knit hat, sunglasses,
  parasol, fan, mask, raincoat and more, driven by the weather)
- Home screen widget with the same conclusion and character
- A built-in appearance test screen (Settings) to preview every part combination
- Data: Open-Meteo, with automatic fallback to MET Norway

## Tech

- Android, Kotlin, plain Views (no Compose)
- Package: `com.taigashinohara.tenkinokotae`

## License

MIT — see [LICENSE](LICENSE).
