# Jin Translate

Fabric mod that auto-translates chat. English messages get translated to Japanese, and anything you type in Japanese gets sent as English.

Built for 1.21.8 but probably works on nearby versions too.

## What it does

- incoming chat (en → ja): shows a translated line below the original message
- outgoing chat: type in Japanese, it sends in English automatically

Uses [MyMemory](https://mymemory.translated.net/) so no API key needed.

## Install

Drop the jar into your `mods/` folder. Needs [Fabric API](https://modrinth.com/mod/fabric-api).

## Build

```
./gradlew build
```

jar ends up in `build/libs/`.

## Notes

Translation quality depends on MyMemory. Short phrases sometimes come out weird. Free tier has a daily limit so if it stops working just wait til tomorrow.

Detection is based on unicode blocks (hiragana/katakana/kanji) so romaji won't trigger the outgoing translation.

## License

MIT
