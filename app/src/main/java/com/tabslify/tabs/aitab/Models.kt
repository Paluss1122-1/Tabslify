package com.tabslify.tabs.aitab

import com.tabslify.quiethoursnotificationhelper.OPENROUTER_DEFAULT_MODEL

data class Model(
    val realname: String,
    val vision: Boolean = false,
    val audio: Boolean = false,
    val weight: Int = 1,
    val name: String = realname.substringAfter("/", realname)
)

val geminiModels = listOf(
    Model("gemini-3.8-flash", vision = true, audio = true, weight = 3),
    Model("gemini-3.7-flash", vision = true, audio = true, weight = 3),
    Model("gemini-3.6-flash", vision = true, audio = true, weight = 2),
    Model("gemini-3.1-flash-lite", vision = true, audio = true, weight = 2),
    Model("gemini-3-flash-preview", vision = true, audio = true, weight = 1),
    Model("gemini-2.5-flash", vision = true, audio = true, weight = 1),
    Model("gemini-3.5-flash-lite", vision = true, audio = true, weight = 1)
)

val nvidiaModels = listOf(
    Model("openai/gpt-oss-20b", weight = 1),
    Model("z-ai/glm-5-3", weight = 5),
    Model("meta/llama-3.2-11b-vision-instruct", vision = true, weight = 2),
    Model("nvidia/nemotron-3-nano-omni-30b-a3b-reasoning", vision = true, weight = 2)
)

val openrouterModels = listOf(
    Model(OPENROUTER_DEFAULT_MODEL, weight = 1, name = "Nemotron 3 Ultra 550B"),
    Model("nvidia/nemotron-3.5-lightning:free", weight = 1, name = "Nemotron 3.5 Lightning"),
    Model("google/gemma-4-31b-it:free", vision = true, weight = 1, name = "Gemma 4 31B")
)

val serverModels = listOf(
    Model("qwen2.5:7b"),
    Model("qwen2.5-coder:3b"),
    Model("qwen2.5-coder:7b"),
    Model("qwen2.5-coder:14b"),
    Model("qwen3-coder-next:cloud"),
    Model("qwen3-vl:235b-cloud", true),
    Model("llava:13b", true),
    Model("llama3.2-vision:11b", true),
)