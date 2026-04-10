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
    val qwen25_15bInstruct = DownloadableModel(
        id = "qwen25-1-5b-instruct",
        displayName = "Qwen2.5 1.5B Instruct",
        repoId = "litert-community/Qwen2.5-1.5B-Instruct",
        version = "main",
        storageDirName = "qwen25-1-5b-instruct",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        sizeLabel = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        sizeBytes = 0L,
        summary = "A LiteRT-LM bundle based on Qwen2.5-1.5B-Instruct.",
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
        modelPageUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
    )

    val all = listOf(qwen25_15bInstruct)
}
