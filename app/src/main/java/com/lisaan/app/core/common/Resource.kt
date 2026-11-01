package com.lisaan.app.core.common

sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val messageAr: String, val messageEn: String, val throwable: Throwable? = null) : Resource<Nothing>()
}
