package com.eslab.universe.data

data class DownloadableModel(
    val id: String,
    val displayName: String,
    val repoId: String,
    val version: String,
    val storageDirName: String,
    val fileName: String,
    val sizeLabel: String,
    val sizeBytes: Long,
    val summary: String,
    val downloadUrl: String,
    val modelPageUrl: String,
    val accessToken: String? = null,
)

object ModelCatalog {
    val qwen35_08bLiteRt = DownloadableModel(
        id = "qwen35-0-8b-litert",
        displayName = "Qwen3.5 0.8B LiteRT",
        repoId = "GabrieleConte/Qwen3.5-0.8B-LiteRT",
        version = "main",
        storageDirName = "qwen35-0-8b-litert",
        fileName = "qwen35_mm_q8_ekv2048.litertlm",
        sizeLabel = "~1.2 GB",
        sizeBytes = 1_200L * 1024L * 1024L,
        summary = "A multimodal LiteRT-LM bundle based on Qwen3.5-0.8B.",
        downloadUrl = "https://huggingface.co/GabrieleConte/Qwen3.5-0.8B-LiteRT/resolve/main/qwen35_mm_q8_ekv2048.litertlm?download=true",
        modelPageUrl = "https://huggingface.co/GabrieleConte/Qwen3.5-0.8B-LiteRT",
    )

    val all = listOf(qwen35_08bLiteRt)
}
